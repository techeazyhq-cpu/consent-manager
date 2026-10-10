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

package com.techeazyhq.cm.aud.infra;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.techeazyhq.cm.aud.dom.AudChain;
import com.techeazyhq.cm.aud.dom.AudDraft;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

/**
 * Turns an event as published on Pulsar into an audit draft (ADR-014). Every platform event carries {@code evtId}
 * and {@code occurredAt}; the request, artefact, fiduciary and principal ids are taken where the event has them. A
 * consent token ({@code cnsTkn}) is a credential, so the ledger keeps only its SHA-256 ({@code cnsTknSha256}): enough
 * to prove which token was issued, useless to present.
 */
public class AudEvtParser {

    private final ObjectMapper objectMapper;

    public AudEvtParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AudDraft parse(String evtType, String json) throws IOException {
        JsonNode node = objectMapper.readTree(json);
        if (!(node instanceof ObjectNode event)) {
            throw new IOException("An event is a JSON object");
        }
        String token = text(event, "cnsTkn");
        if (token != null) {
            event.remove("cnsTkn");
            event.put("cnsTknSha256", AudChain.sha256Hex(token));
        }
        String evtId = text(event, "evtId");
        String occurredAt = text(event, "occurredAt");
        if (evtId == null || occurredAt == null) {
            throw new IOException(evtType + " has no evtId or occurredAt");
        }
        return new AudDraft(UUID.fromString(evtId), evtType, Instant.parse(occurredAt), text(event, "cnsReqId"),
                text(event, "cnsArtId"), text(event, "dfId"), dpIdOf(event), objectMapper.writeValueAsString(event));
    }

    private static String dpIdOf(JsonNode event) {
        String direct = text(event, "dpId");
        return direct != null ? direct : text(event.path("dpRef"), "dpId");
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
