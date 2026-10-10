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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeazyhq.cm.aud.dom.AudChain;
import com.techeazyhq.cm.aud.dom.AudDraft;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AudEvtParserTest {

    private final AudEvtParser parser = new AudEvtParser(new ObjectMapper());

    @Test
    void aGrantKeepsItsIdsAndOnlyTheDigestOfItsConsentToken() throws IOException {
        String json = """
                {"evtId":"6f1c2b9e-1d6a-4a8e-9f2a-0d4b3c2a1e10","occurredAt":"2026-10-10T12:00:00.123456789Z",
                 "cnsReqId":"req-1","cnsArtId":"art-1","dfId":"df-1","dpRef":{"dpId":"dp-1","typ":null,"val":null},
                 "cnsTkn":"header.payload.signature","valPrd":{"from":"2026-10-10T12:00:00Z","until":"2027-10-10T12:00:00Z"}}
                """;

        AudDraft draft = parser.parse("CnsGrantedEvt", json);

        assertEquals("CnsGrantedEvt", draft.evtType());
        assertEquals("req-1", draft.cnsReqId());
        assertEquals("art-1", draft.cnsArtId());
        assertEquals("df-1", draft.dfId());
        assertEquals("dp-1", draft.dpId());
        assertEquals(Instant.parse("2026-10-10T12:00:00.123456Z"), draft.occurredAt());
        assertFalse(draft.payloadJson().contains("header.payload.signature"));
        assertTrue(draft.payloadJson().contains("\"cnsTknSha256\":\"" + AudChain.sha256Hex("header.payload.signature") + "\""));
    }

    @Test
    void anUnlinkedPrincipalHasNoPrincipalId() throws IOException {
        String json = """
                {"evtId":"6f1c2b9e-1d6a-4a8e-9f2a-0d4b3c2a1e11","occurredAt":"2026-10-10T12:00:00Z","cnsReqId":"req-2",
                 "dfId":"df-1","dpRef":{"dpId":null,"typ":"EMAIL","val":"ann@example.com"}}
                """;

        AudDraft draft = parser.parse("CnsReqCreatedEvt", json);

        assertNull(draft.dpId());
        assertNull(draft.cnsArtId());
    }

    @Test
    void aFiduciaryActivationNeedsNoConsentIds() throws IOException {
        AudDraft draft = parser.parse("DfActivatedEvt",
                "{\"evtId\":\"6f1c2b9e-1d6a-4a8e-9f2a-0d4b3c2a1e12\",\"occurredAt\":\"2026-10-10T12:00:00Z\",\"dfId\":\"df-9\",\"name\":\"Acme\"}");

        assertEquals("df-9", draft.dfId());
        assertNull(draft.cnsReqId());
    }

    @Test
    void anEventWithoutItsIdOrTimeIsRefused() {
        assertThrows(IOException.class, () -> parser.parse("CnsDeniedEvt", "{\"cnsReqId\":\"req-1\"}"));
        assertThrows(IOException.class, () -> parser.parse("CnsDeniedEvt", "[1,2]"));
    }
}
