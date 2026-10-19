/*
 * Copyright 2026 Vasantha Kumar
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * @author Vasantha Kumar <vasantha.kumar@hotmail.com>
 */

package com.techeazyhq.cm.aud.ifc.rest;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeazyhq.cm.aud.app.AudLedgerService;
import com.techeazyhq.cm.aud.dom.AudEntry;
import com.techeazyhq.cm.aud.dom.AudQuery;
import com.techeazyhq.cm.aud.dom.AudRecord;
import com.techeazyhq.cm.audkit.PlatformActionEvt;
import com.techeazyhq.cm.seckit.AuthCtx;

import reactor.core.publisher.Mono;

/**
 * {@code GET /api/aud/activity}: the ledger as people read it (ADR-040), newest first: what happened, to whom, who did
 * it and the facts recorded with it. Platform staff see every tenant, or one ({@code dfId}). A fiduciary's Owners,
 * Administrators, Auditors and its systems see their own. Nothing from a principal's contact details is shown: an
 * action's details never hold them, and for the consent lifecycle's events only the purpose is.
 *
 * <p>To a fiduciary, platform staff are "Platform staff", not a named person.
 */
public class AudActivityHttpHandler {

    private static final Set<String> DF_READERS = Set.of("DF_OWNER", "DF_ADMIN", "DF_AUDITOR");
    private static final ObjectMapper JSON = new ObjectMapper();

    private final AudLedgerService ledger;

    public AudActivityHttpHandler(AudLedgerService ledger) {
        this.ledger = ledger;
    }

    public Mono<ServerResponse> activity(ServerRequest request) {
        return request.principal().cast(JwtAuthenticationToken.class)
                .map(token -> AuthCtx.from((Jwt) token.getPrincipal()))
                .flatMap(ctx -> {
                    String dfId;
                    if (ctx.isPlatformStaff()) {
                        dfId = request.queryParam("dfId").filter(s -> !s.isBlank()).orElse(null);
                    } else if (mayReadOwn(ctx)) {
                        dfId = ctx.dfId();
                    } else {
                        return ServerResponse.status(403).bodyValue(Map.of("type", "urn:cm:problem:aud-forbidden",
                                "title", "aud-forbidden",
                                "detail", "Owners, Administrators and Auditors at a fiduciary, and platform staff, read the audit"));
                    }
                    AudQuery query = new AudQuery(null, null, dfId, null,
                            request.queryParam("limit").map(AudActivityHttpHandler::number).map(Long::intValue).orElse(50),
                            category(request.queryParam("category").orElse(null)),
                            request.queryParam("before").map(AudActivityHttpHandler::number).orElse(null));
                    boolean staff = ctx.isPlatformStaff();
                    return ledger.findRecords(query).map(r -> view(r, staff)).collectList()
                            .flatMap(list -> ServerResponse.ok().bodyValue(list));
                });
    }

    /** A fiduciary's system (no person role) may read; of its people, those who look after it. */
    static boolean mayReadOwn(AuthCtx ctx) {
        if (!ctx.isDf()) {
            return false;
        }
        boolean person = ctx.roles().stream().anyMatch(r -> r.startsWith("DF_"));
        return !person || ctx.roles().stream().anyMatch(DF_READERS::contains);
    }

    static Map<String, Object> view(AudRecord record, boolean forStaff) {
        AudEntry e = record.entry();
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("seq", e.seq());
        view.put("evtType", e.evtType());
        view.put("occurredAt", e.occurredAt());
        view.put("dfId", e.dfId());
        view.put("cnsReqId", e.cnsReqId());
        view.put("entryHash", e.entryHash());
        JsonNode payload = read(record.payloadJson());
        Map<String, String> details = new LinkedHashMap<>();
        if (PlatformActionEvt.isAction(e.evtType())) {
            JsonNode actor = payload.path("actor");
            String kind = actor.path("kind").asText("SYSTEM");
            String name = "STAFF".equals(kind) && !forStaff ? "Platform staff" : actor.path("name").asText(null);
            view.put("actor", actorView(kind, name));
            view.put("target", payload.path("target").asText(null));
            payload.path("details").fields().forEachRemaining(f -> details.put(f.getKey(), f.getValue().asText()));
        } else {
            view.put("actor", consentActor(e.evtType()));
            // a request carries its purpose as prps.code; some events flatten it to prpsCode
            JsonNode purpose = payload.hasNonNull("prpsCode") ? payload.get("prpsCode") : payload.path("prps").path("code");
            if (!purpose.isMissingNode() && !purpose.isNull()) {
                details.put("purpose", purpose.asText());
            }
            if (payload.hasNonNull("channel")) {
                details.put("channel", payload.get("channel").asText());
            }
            if (forStaff && payload.hasNonNull("code")) {
                details.put("code", payload.get("code").asText());
            }
        }
        view.put("details", details);
        // an erased payload leaves the entry and its hash; the facts are gone
        view.put("erased", record.payloadJson() == null);
        return view;
    }

    /** The consent lifecycle's events don't name who acted, but which party did is plain from the event. */
    private static Map<String, String> consentActor(String evtType) {
        return switch (evtType) {
            case "CnsReqCreatedEvt" -> actorView("DF", "The fiduciary");
            case "CnsGrantedEvt", "CnsDeniedEvt", "CnsRevokedEvt", "CnsDpLinkedEvt" -> actorView("DP", "The person");
            default -> actorView("SYSTEM", "Consent Manager");
        };
    }

    private static Map<String, String> actorView(String kind, String name) {
        Map<String, String> actor = new LinkedHashMap<>();
        actor.put("kind", kind);
        actor.put("name", name);
        return actor;
    }

    private static JsonNode read(String json) {
        if (json == null) {
            return JSON.createObjectNode();
        }
        try {
            return JSON.readTree(json);
        } catch (Exception e) {
            return JSON.createObjectNode();
        }
    }

    private static AudQuery.Category category(String value) {
        if (value == null || value.isBlank()) {
            return AudQuery.Category.ALL;
        }
        try {
            return AudQuery.Category.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return AudQuery.Category.ALL;
        }
    }

    private static Long number(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
