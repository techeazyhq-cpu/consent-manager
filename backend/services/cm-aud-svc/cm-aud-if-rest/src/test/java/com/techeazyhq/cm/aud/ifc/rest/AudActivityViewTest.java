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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import com.techeazyhq.cm.aud.dom.AudEntry;
import com.techeazyhq.cm.aud.dom.AudRecord;
import com.techeazyhq.cm.seckit.AuthCtx;

/** ADR-040: the ledger as people read it, and who may. */
class AudActivityViewTest {

    private static AudRecord record(String evtType, String payload) {
        AudEntry entry = new AudEntry(7, UUID.randomUUID(), evtType, Instant.parse("2026-10-19T08:00:00Z"),
                Instant.parse("2026-10-19T08:00:01Z"), null, null, "df-1", "sha", "prev", "hash");
        return new AudRecord(entry, payload);
    }

    private static final String APPROVED = """
            {"action":"plan-change.approved","target":"req-1","actor":{"kind":"STAFF","id":"s-1","name":"admin@cm.local"},
             "details":{"from":"FREE","to":"STANDARD"}}""";

    @Test
    void staffSeeWhoActed_fiduciariesSeePlatformStaff() {
        Map<String, Object> forStaff = AudActivityHttpHandler.view(record("plan-change.approved", APPROVED), true);
        Map<String, Object> forDf = AudActivityHttpHandler.view(record("plan-change.approved", APPROVED), false);

        assertEquals(Map.of("kind", "STAFF", "name", "admin@cm.local"), forStaff.get("actor"));
        assertEquals(Map.of("kind", "STAFF", "name", "Platform staff"), forDf.get("actor"));
        assertEquals(Map.of("from", "FREE", "to", "STANDARD"), forDf.get("details"));
        assertEquals("req-1", forDf.get("target"));
    }

    @Test
    void aConsentEventShowsItsPurposeAndParty_neverTheContact() {
        Map<String, Object> view = AudActivityHttpHandler.view(record("CnsReqCreatedEvt",
                "{\"prps\":{\"code\":\"ORDER_UPDATES\"},\"dpRef\":{\"typ\":\"EMAIL\",\"val\":\"asha@example.com\"}}"), true);

        assertEquals(Map.of("purpose", "ORDER_UPDATES"), view.get("details"));
        assertEquals("The fiduciary", ((Map<?, ?>) view.get("actor")).get("name"));
        assertFalse(view.toString().contains("asha@example.com"));
    }

    @Test
    void anErasedPayloadLeavesTheEntry() {
        Map<String, Object> view = AudActivityHttpHandler.view(record("CnsGrantedEvt", null), true);
        assertEquals(true, view.get("erased"));
        assertEquals(Map.of(), view.get("details"));
    }

    private static AuthCtx df(List<String> roles) {
        return AuthCtx.from(Jwt.withTokenValue("t").header("alg", "RS256").subject("u-1")
                .issuer("http://localhost:8080/realms/cm-df").issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60)).claim("roles", roles).claim("df_id", "df-1").build());
    }

    @Test
    void ownersAdministratorsAuditorsAndSystemsReadTheirOwn_consentManagersAndDevelopersDont() {
        assertTrue(AudActivityHttpHandler.mayReadOwn(df(List.of("DF", "DF_AUDITOR"))));
        assertTrue(AudActivityHttpHandler.mayReadOwn(df(List.of("DF", "DF_OWNER"))));
        assertTrue(AudActivityHttpHandler.mayReadOwn(df(List.of("DF"))), "an API client");
        assertFalse(AudActivityHttpHandler.mayReadOwn(df(List.of("DF", "DF_CONSENT_MANAGER"))));
        assertFalse(AudActivityHttpHandler.mayReadOwn(df(List.of("DF", "DF_DEVELOPER"))));
    }
}
