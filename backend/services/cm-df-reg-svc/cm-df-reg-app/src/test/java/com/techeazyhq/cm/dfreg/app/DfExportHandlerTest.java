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

package com.techeazyhq.cm.dfreg.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.techeazyhq.cm.dfreg.dom.DataExportPort;
import com.techeazyhq.cm.dfreg.dom.Df;
import com.techeazyhq.cm.dfreg.dom.DfApiKey;
import com.techeazyhq.cm.dfreg.dom.DfApiKeyRepoPort;
import com.techeazyhq.cm.dfreg.dom.DfFactory;
import com.techeazyhq.cm.dfreg.dom.DfMember;
import com.techeazyhq.cm.dfreg.dom.DfMemberDirectoryPort;
import com.techeazyhq.cm.dfreg.dom.DfPrfl;
import com.techeazyhq.cm.dfreg.dom.DfRepoPort;
import com.techeazyhq.cm.dfreg.dom.DfRole;
import com.techeazyhq.cm.dfreg.dom.DfSts;
import com.techeazyhq.cm.dfreg.dom.DfTermsAcceptance;
import com.techeazyhq.cm.dfreg.dom.DfTermsRepoPort;
import com.techeazyhq.cm.dfreg.dom.NewDfMember;
import com.techeazyhq.cm.dfreg.dom.TermsDoc;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class DfExportHandlerTest {

    private static final Instant NOW = Instant.parse("2026-11-08T09:00:00Z");
    private static final String DF = "df-1";

    private final Map<String, Df> dfs = new LinkedHashMap<>();
    private final List<String> audited = new ArrayList<>();

    private final DfRepoPort repo = new DfRepoPort() {
        @Override
        public Mono<Df> save(Df df) {
            dfs.put(df.id(), df);
            return Mono.just(df);
        }

        @Override
        public Mono<Df> findById(String id) {
            return Mono.justOrEmpty(dfs.get(id));
        }

        @Override
        public Flux<Df> findAll() {
            return Flux.fromIterable(dfs.values());
        }
    };

    private final DfMemberDirectoryPort members = new DfMemberDirectoryPort() {
        @Override
        public Mono<DfMember> invite(String dfId, NewDfMember member) {
            return Mono.empty();
        }

        @Override
        public Flux<DfMember> list(String dfId) {
            return Flux.just(new DfMember("m1", dfId, "owner@acme.example", "Owner", DfRole.OWNER));
        }

        @Override
        public Mono<DfMember> find(String dfId, String memberId) {
            return Mono.empty();
        }

        @Override
        public Mono<DfMember> changeRole(DfMember member, DfRole role) {
            return Mono.empty();
        }

        @Override
        public Mono<Void> remove(DfMember member) {
            return Mono.empty();
        }
    };

    private final DfApiKeyRepoPort keys = new DfApiKeyRepoPort() {
        @Override
        public Mono<DfApiKey> save(DfApiKey key) {
            return Mono.just(key);
        }

        @Override
        public Mono<DfApiKey> findById(String id) {
            return Mono.empty();
        }

        @Override
        public Mono<DfApiKey> findByHash(String keyHash) {
            return Mono.empty();
        }

        @Override
        public Flux<DfApiKey> findByDfId(String dfId) {
            return Flux.just(DfApiKey.rehydrate("k1", dfId, "Orders", "cmk_abcd", "hash-must-not-leak", "SEALED-MUST-NOT-LEAK", true,
                    NOW, "owner", null, null));
        }

        @Override
        public Mono<Void> touch(String id, Instant at) {
            return Mono.empty();
        }
    };

    private final DfTermsRepoPort terms = new DfTermsRepoPort() {
        @Override
        public Mono<DfTermsAcceptance> saveIfAbsent(DfTermsAcceptance a) {
            return Mono.just(a);
        }

        @Override
        public Mono<Long> deleteByDfId(String dfId) {
            return Mono.just(0L);
        }

        @Override
        public Flux<DfTermsAcceptance> findByDfId(String dfId) {
            return Flux.just(DfTermsAcceptance.of(dfId, TermsDoc.TERMS, "2026-11", "owner-1", NOW));
        }
    };

    private final DataExportPort data = new DataExportPort() {
        @Override
        public Mono<Map<String, Object>> consentsAndPurposes(String dfId) {
            return Mono.just(Map.of("consents", Map.of("count", 3, "truncated", false, "items", List.of()),
                    "purposes", List.of(Map.of("code", "ORDER_UPDATES")), "receiptKeys", "/jwks"));
        }

        @Override
        public Mono<Map<String, Object>> auditEntries(String dfId) {
            return Mono.just(Map.of("count", 7, "truncated", true, "items", List.of()));
        }
    };

    private final DfExportHandler handler = new DfExportHandler(repo, members, keys, terms, data,
            (action, dfId, target, details) -> {
                audited.add(action + " " + details);
                return Mono.empty();
            }, Clock.fixed(NOW, ZoneOffset.UTC));

    private void df(DfSts sts) {
        dfs.put(DF, DfFactory.rehydrate(DF, "Acme Ltd", "ops@acme.example", new DfPrfl("Acme Limited", null, null, null, null, null, null, null, null),
                null, sts, NOW, "client-secret-must-not-leak", NOW));
    }

    @Test
    void anOwnerGetsTheWholeBundleAndItIsAudited() {
        df(DfSts.ACTIVE);

        Map<String, Object> bundle = handler.export(DfTeamCaller.member("owner-1", DF, DfRole.OWNER)).block();

        assertEquals("cm:FiduciaryExport", bundle.get("@type"));
        assertEquals(NOW.toString(), bundle.get("exportedAt"));
        @SuppressWarnings("unchecked")
        Map<String, Object> profile = (Map<String, Object>) bundle.get("fiduciary");
        assertEquals("Acme Ltd", profile.get("name"));
        assertEquals("Acme Limited", profile.get("legalName"));
        assertEquals(1, ((List<?>) bundle.get("team")).size());
        assertEquals(1, ((List<?>) bundle.get("apiKeys")).size());
        assertEquals(1, ((List<?>) bundle.get("termsAcceptances")).size());
        assertEquals(1, ((List<?>) bundle.get("purposes")).size());
        assertTrue(bundle.containsKey("consents") && bundle.containsKey("audit") && bundle.containsKey("notes"));
        assertTrue(audited.get(0).startsWith("tenant.exported"));
        assertTrue(audited.get(0).contains("consents=3") && audited.get(0).contains("auditEntries=7"));
    }

    @Test
    void noSecretIsEverInTheBundle() {
        df(DfSts.ACTIVE);

        String text = handler.export(DfTeamCaller.member("owner-1", DF, DfRole.OWNER)).block().toString();

        assertFalse(text.contains("SEALED-MUST-NOT-LEAK"));
        assertFalse(text.contains("hash-must-not-leak"));
        assertFalse(text.contains("client-secret-must-not-leak"));
        assertTrue(text.contains("cmk_abcd"), "the key is listed by its prefix only");
    }

    @Test
    void anAdministratorMayExportAndOthersMayNot() {
        df(DfSts.LEAVING);

        assertEquals("cm:FiduciaryExport", handler.export(DfTeamCaller.member("a", DF, DfRole.ADMIN)).block().get("@type"));
        for (DfRole role : List.of(DfRole.CONSENT_MANAGER, DfRole.DEVELOPER, DfRole.AUDITOR)) {
            DomRuleViolation v = assertThrows(DomRuleViolation.class, () -> handler.export(DfTeamCaller.member("u", DF, role)).block());
            assertEquals(403, v.httpStatus());
        }
        assertEquals(403, assertThrows(DomRuleViolation.class, () -> handler.export(DfTeamCaller.staff("s")).block()).httpStatus());
    }

    @Test
    void itIsAvailableWhileActiveSuspendedOrLeavingButNotAfterClosing() {
        for (DfSts sts : List.of(DfSts.ACTIVE, DfSts.SUSPENDED, DfSts.LEAVING)) {
            df(sts);
            assertEquals("cm:FiduciaryExport", handler.export(DfTeamCaller.member("o", DF, DfRole.OWNER)).block().get("@type"), sts.name());
        }
        for (DfSts sts : List.of(DfSts.CLOSED, DfSts.PENDING)) {
            df(sts);
            DomRuleViolation v = assertThrows(DomRuleViolation.class, () -> handler.export(DfTeamCaller.member("o", DF, DfRole.OWNER)).block());
            assertEquals(409, v.httpStatus());
            assertEquals("export-unavailable", v.problemType());
        }
    }
}
