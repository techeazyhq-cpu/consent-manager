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
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.techeazyhq.cm.audkit.AuditTrail;
import com.techeazyhq.cm.dfreg.dom.DfRole;
import com.techeazyhq.cm.dfreg.dom.DfTermsAcceptance;
import com.techeazyhq.cm.dfreg.dom.DfTermsRepoPort;
import com.techeazyhq.cm.dfreg.dom.TermsDoc;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class DfTermsHandlerTest {

    private static final String DF = "df-1";

    private final List<DfTermsAcceptance> stored = new ArrayList<>();
    private final List<String> audited = new ArrayList<>();

    private final DfTermsRepoPort repo = new DfTermsRepoPort() {
        @Override
        public Mono<DfTermsAcceptance> saveIfAbsent(DfTermsAcceptance a) {
            return Mono.fromSupplier(() -> stored.stream()
                    .filter(s -> s.dfId().equals(a.dfId()) && s.doc() == a.doc() && s.version().equals(a.version()))
                    .findFirst().orElseGet(() -> {
                        stored.add(a);
                        return a;
                    }));
        }

        @Override
        public Flux<DfTermsAcceptance> findByDfId(String dfId) {
            return Flux.fromIterable(stored).filter(a -> a.dfId().equals(dfId));
        }
    };

    private final AuditTrail audit = (action, dfId, target, details) -> {
        audited.add(action + " " + details);
        return Mono.empty();
    };

    private final DfTermsHandler handler = new DfTermsHandler(repo, Map.of(TermsDoc.TERMS, "2026-11", TermsDoc.DPA, "2026-11"),
            audit, Clock.fixed(Instant.parse("2026-11-07T10:00:00Z"), ZoneOffset.UTC));

    private static DfTeamCaller owner() {
        return DfTeamCaller.member("owner-1", DF, DfRole.OWNER);
    }

    private static void violation(int status, String code, Runnable r) {
        DomRuleViolation e = assertThrows(DomRuleViolation.class, r::run);
        assertEquals(status, e.httpStatus());
        assertEquals(code, e.problemType());
    }

    @Test
    void nothingIsAcceptedAtFirst() {
        List<DfTermsHandler.Status> status = handler.status(owner()).block();

        assertEquals(2, status.size());
        assertTrue(status.stream().noneMatch(DfTermsHandler.Status::accepted));
    }

    @Test
    void anOwnerAcceptsTheCurrentVersionAndItIsRecordedWithWhoAndWhen() {
        DfTermsAcceptance a = handler.accept(owner(), "dpa", "2026-11").block();

        assertEquals(TermsDoc.DPA, a.doc());
        assertEquals("owner-1", a.acceptedBy());
        assertEquals(Instant.parse("2026-11-07T10:00:00Z"), a.acceptedAt());
        assertEquals(1, audited.size());
        assertTrue(audited.get(0).startsWith("terms.accepted"));
        DfTermsHandler.Status dpa = handler.status(owner()).block().stream().filter(s -> s.doc() == TermsDoc.DPA).findFirst().orElseThrow();
        assertTrue(dpa.accepted());
        assertFalse(handler.status(owner()).block().stream().filter(s -> s.doc() == TermsDoc.TERMS).findFirst().orElseThrow().accepted());
    }

    @Test
    void acceptingAgainChangesNothingAndIsNotAuditedTwice() {
        DfTermsAcceptance first = handler.accept(owner(), "terms", "2026-11").block();
        DfTermsAcceptance again = handler.accept(DfTeamCaller.member("owner-2", DF, DfRole.OWNER), "TERMS", "2026-11").block();

        assertEquals(first.id(), again.id());
        assertEquals("owner-1", again.acceptedBy());
        assertEquals(1, stored.size());
        assertEquals(1, audited.size());
    }

    @Test
    void anOldVersionCannotBeAccepted() {
        violation(409, "terms-version-outdated", () -> handler.accept(owner(), "terms", "2025-01").block());

        assertTrue(stored.isEmpty());
    }

    @Test
    void anUnknownDocumentIsRefused() {
        violation(400, "terms-document-unknown", () -> handler.accept(owner(), "privacy", "2026-11").block());
    }

    @Test
    void onlyAnOwnerAccepts() {
        for (DfRole role : List.of(DfRole.ADMIN, DfRole.CONSENT_MANAGER, DfRole.DEVELOPER, DfRole.AUDITOR)) {
            violation(403, "not-permitted", () -> handler.accept(DfTeamCaller.member("u", DF, role), "terms", "2026-11").block());
        }
        violation(403, "not-permitted", () -> handler.accept(DfTeamCaller.staff("staff-1"), "terms", "2026-11").block());
        assertTrue(stored.isEmpty());
    }

    @Test
    void anyMemberSeesTheStatusButStaffUseTheEvidenceView() {
        handler.accept(owner(), "terms", "2026-11").block();

        assertTrue(handler.status(DfTeamCaller.member("a", DF, DfRole.AUDITOR)).block().get(0).accepted());
        violation(403, "not-permitted", () -> handler.status(DfTeamCaller.staff("staff-1")).block());
        assertEquals(1, handler.evidence(DfTeamCaller.staff("staff-1"), DF).block().size());
        violation(403, "not-permitted", () -> handler.evidence(owner(), DF).block());
    }

    @Test
    void anotherFiduciaryDoesNotSeeThisOnesAcceptance() {
        handler.accept(owner(), "terms", "2026-11").block();

        assertTrue(handler.status(DfTeamCaller.member("x", "df-2", DfRole.OWNER)).block().stream().noneMatch(DfTermsHandler.Status::accepted));
    }

    @Test
    void whenTheWordsChangeTheNewVersionNeedsAcceptingAgain() {
        handler.accept(owner(), "terms", "2026-11").block();
        DfTermsHandler v2 = new DfTermsHandler(repo, Map.of(TermsDoc.TERMS, "2027-03", TermsDoc.DPA, "2026-11"), audit,
                Clock.systemUTC());

        assertFalse(v2.status(owner()).block().get(0).accepted());
        v2.accept(owner(), "terms", "2027-03").block();
        assertTrue(v2.status(owner()).block().get(0).accepted());
        assertEquals(2, v2.evidence(DfTeamCaller.staff("s"), DF).block().size(), "the earlier acceptance stays as evidence");
    }
}
