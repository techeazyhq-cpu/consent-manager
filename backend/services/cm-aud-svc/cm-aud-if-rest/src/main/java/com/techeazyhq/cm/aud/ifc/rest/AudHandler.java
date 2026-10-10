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

import com.techeazyhq.cm.aud.app.AudAnchoringService;
import com.techeazyhq.cm.aud.app.AudLedgerService;
import com.techeazyhq.cm.aud.dom.AudChain;
import com.techeazyhq.cm.aud.dom.AudCheckpointPort;
import com.techeazyhq.cm.aud.dom.AudEntry;
import com.techeazyhq.cm.aud.dom.AudQuery;
import com.techeazyhq.cm.seckit.AuthCtx;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import reactor.core.publisher.Mono;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code /api/aud/**}: reading and verifying the audit ledger (ADR-014) and its anchored checkpoints (ADR-015). Who sees what comes from the caller's own JWT,
 * never from a parameter: an administrator may look up any entry and verify the whole chain; a fiduciary sees the
 * entries about itself; a principal sees the entries about them. Anyone signed in may read the chain head, so outsiders
 * can note it and later check that the history up to it has not changed.
 */
public class AudHandler {

    private final AudLedgerService ledger;
    private final AudAnchoringService anchoring;

    public AudHandler(AudLedgerService ledger, AudAnchoringService anchoring) {
        this.ledger = ledger;
        this.anchoring = anchoring;
    }

    public Mono<ServerResponse> entries(ServerRequest request) {
        return authCtx(request).flatMap(ctx -> {
            String cnsReqId = request.queryParam("cnsReqId").orElse(null);
            String cnsArtId = request.queryParam("cnsArtId").orElse(null);
            int limit = request.queryParam("limit").map(AudHandler::parseLimit).orElse(100);
            AudQuery query;
            if (ctx.isCmAdmin()) {
                query = new AudQuery(cnsReqId, cnsArtId, request.queryParam("dfId").orElse(null), null, limit);
            } else if (ctx.isDf()) {
                query = new AudQuery(cnsReqId, cnsArtId, ctx.dfId(), null, limit);
            } else if (ctx.isDp()) {
                query = new AudQuery(cnsReqId, cnsArtId, null, ctx.dpId(), limit);
            } else {
                return forbidden();
            }
            return ledger.find(query).collectList().flatMap(entries -> ServerResponse.ok().bodyValue(entries));
        });
    }

    public Mono<ServerResponse> head(ServerRequest request) {
        return ledger.head()
                .map(entry -> Map.<String, Object>of("seq", entry.seq(), "entryHash", entry.entryHash()))
                .defaultIfEmpty(Map.of("seq", 0L, "entryHash", AudChain.GENESIS_HASH))
                .flatMap(head -> ServerResponse.ok().bodyValue(head));
    }

    /** The chain and the checkpoints with their external timestamps (ADR-015), each with its own verdict. */
    public Mono<ServerResponse> verify(ServerRequest request) {
        return authCtx(request).flatMap(ctx -> ctx.isCmAdmin()
                ? ledger.verify().zipWith(anchoring.verify())
                        .flatMap(v -> ServerResponse.ok().bodyValue(Map.of("chain", v.getT1(), "checkpoints", v.getT2())))
                : forbidden());
    }

    /**
     * Everything needed to prove offline that an entry is in the ledger as an external authority timestamped it: the
     * entry, its Merkle path to the checkpoint's root, the checkpoint, and the RFC 3161 token over the checkpoint's
     * hash. Only for whom the entry concerns: an administrator, its fiduciary, or its principal.
     */
    public Mono<ServerResponse> proof(ServerRequest request) {
        long seq;
        try {
            seq = Long.parseLong(request.pathVariable("seq"));
        } catch (NumberFormatException e) {
            return ServerResponse.badRequest().build();
        }
        return authCtx(request).flatMap(ctx -> anchoring.proof(seq)
                .flatMap(proof -> mayRead(ctx, proof.entry(), seq).flatMap(allowed -> allowed
                        ? ServerResponse.ok().bodyValue(proofView(proof))
                        : forbidden()))
                .switchIfEmpty(ServerResponse.status(404).bodyValue(Map.of("type", "urn:cm:problem:aud-not-checkpointed",
                        "title", "aud-not-checkpointed",
                        "detail", "Entry " + seq + " does not exist or is not in a checkpoint yet"))));
    }

    /** The checkpoints and their anchors: hashes and timestamps only, nothing personal. */
    public Mono<ServerResponse> checkpoints(ServerRequest request) {
        return anchoring.list().map(AudHandler::checkpointView).collectList()
                .flatMap(list -> ServerResponse.ok().bodyValue(list));
    }

    private Mono<Boolean> mayRead(AuthCtx ctx, AudEntry entry, long seq) {
        if (ctx.isCmAdmin()) {
            return Mono.just(true);
        }
        if (ctx.isDf()) {
            return Mono.just(ctx.dfId().equals(entry.dfId()));
        }
        return ctx.isDp() ? ledger.concerns(seq, ctx.dpId()) : Mono.just(false);
    }

    private static Map<String, Object> proofView(AudAnchoringService.InclusionProof proof) {
        Map<String, Object> merkle = new LinkedHashMap<>();
        merkle.put("leaf", proof.entry().entryHash());
        merkle.put("path", proof.path());
        merkle.put("root", proof.checkpoint().checkpoint().merkleRoot());
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("entry", proof.entry());
        view.put("merkle", merkle);
        view.putAll(checkpointView(proof.checkpoint()));
        return view;
    }

    private static Map<String, Object> checkpointView(AudCheckpointPort.Anchored anchored) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("checkpoint", anchored.checkpoint());
        if (anchored.anchor() == null) {
            view.put("anchor", null);
        } else {
            Map<String, Object> anchor = new LinkedHashMap<>();
            anchor.put("authorityUrl", anchored.anchor().authorityUrl());
            anchor.put("timestampedAt", anchored.anchor().timestampedAt());
            anchor.put("tokenBase64", Base64.getEncoder().encodeToString(anchored.anchor().token()));
            view.put("anchor", anchor);
        }
        return view;
    }

    private static int parseLimit(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 100;
        }
    }

    private static Mono<AuthCtx> authCtx(ServerRequest request) {
        return request.principal().cast(JwtAuthenticationToken.class).map(token -> AuthCtx.from((Jwt) token.getPrincipal()));
    }

    private static Mono<ServerResponse> forbidden() {
        return ServerResponse.status(403).bodyValue(Map.of("type", "urn:cm:problem:aud-forbidden", "title", "aud-forbidden"));
    }
}
