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

import com.techeazyhq.cm.dfreg.dom.Df;
import com.techeazyhq.cm.dfreg.dom.DfFactory;
import com.techeazyhq.cm.dfreg.dom.DfMember;
import com.techeazyhq.cm.dfreg.dom.DfMemberDirectoryPort;
import com.techeazyhq.cm.dfreg.dom.DfPrfl;
import com.techeazyhq.cm.dfreg.dom.DfRepoPort;
import com.techeazyhq.cm.dfreg.dom.DfRole;
import com.techeazyhq.cm.dfreg.dom.DfSts;
import com.techeazyhq.cm.dfreg.dom.LeaveProposal;
import com.techeazyhq.cm.dfreg.dom.LeaveProposal.Kind;
import com.techeazyhq.cm.dfreg.dom.LeaveProposalRepoPort;
import com.techeazyhq.cm.dfreg.dom.NewDfMember;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class DfEndContractHandlerTest {

    private static final Instant NOW = Instant.parse("2026-11-08T09:00:00Z");
    private static final String DF = "df-1";

    private final Map<String, Df> dfs = new LinkedHashMap<>();
    private final Map<String, LeaveProposal> stored = new LinkedHashMap<>();
    private final List<String> audited = new ArrayList<>();
    private final List<String> mailed = new ArrayList<>();

    private final DfRepoPort dfRepo = new DfRepoPort() {
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

    private final LeaveProposalRepoPort proposals = new LeaveProposalRepoPort() {
        @Override
        public Mono<LeaveProposal> save(LeaveProposal p) {
            stored.put(p.id(), p);
            return Mono.just(p);
        }

        @Override
        public Mono<LeaveProposal> findById(String id) {
            return Mono.justOrEmpty(stored.get(id));
        }

        @Override
        public Mono<LeaveProposal> findPendingByDfId(String dfId) {
            return Flux.fromIterable(stored.values()).filter(p -> p.dfId().equals(dfId) && p.status() == LeaveProposal.Status.PENDING).next();
        }

        @Override
        public Flux<LeaveProposal> find(LeaveProposal.Status status, String dfId) {
            return Flux.fromIterable(stored.values()).filter(p -> p.status() == status && (dfId == null || p.dfId().equals(dfId)));
        }
    };

    private final DfMemberDirectoryPort team = new DfMemberDirectoryPort() {
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

    private final com.techeazyhq.cm.audkit.AuditTrail audit = (action, dfId, target, details) -> {
        audited.add(action + " " + details);
        return Mono.empty();
    };

    private final DfLeavingHandler leaving = new DfLeavingHandler(dfRepo, team,
            (to, subject, body, ref) -> Mono.fromRunnable(() -> mailed.add(to + ": " + subject)), audit,
            Clock.fixed(NOW, ZoneOffset.UTC), 30);

    private final DfEndContractHandler handler = new DfEndContractHandler(proposals, dfRepo, leaving, audit, Clock.fixed(NOW, ZoneOffset.UTC));

    private void df(DfSts sts) {
        dfs.put(DF, DfFactory.rehydrate(DF, "Acme Ltd", "ops@acme.example", DfPrfl.EMPTY, null, sts, NOW, "secret", NOW));
    }

    private static void refused(int status, String type, Runnable action) {
        DomRuleViolation refusal = assertThrows(DomRuleViolation.class, action::run);
        assertEquals(status, refusal.httpStatus());
        assertEquals(type, refusal.problemType());
    }

    @Test
    void oneMemberProposesAndNothingHappensYet() {
        df(DfSts.ACTIVE);

        LeaveProposal p = handler.propose("alice", DF, Kind.START, " Repeated abuse, case 12 ").block();

        assertEquals(LeaveProposal.Status.PENDING, p.status());
        assertEquals("Repeated abuse, case 12", p.reason());
        assertEquals(DfSts.ACTIVE, dfs.get(DF).sts(), "not until a second person agrees");
        assertTrue(mailed.isEmpty());
        assertTrue(audited.get(0).startsWith("tenant.end-contract-proposed"));
    }

    @Test
    void theProposerCannotDecideItButASecondPersonApprovesAndTheDepartureStarts() {
        df(DfSts.ACTIVE);
        LeaveProposal p = handler.propose("alice", DF, Kind.START, "Repeated abuse").block();

        refused(403, "leave-proposal-same-person", () -> handler.approve("alice", p.id()).block());
        refused(403, "leave-proposal-same-person", () -> handler.reject("alice", p.id(), "x").block());
        assertEquals(DfSts.ACTIVE, dfs.get(DF).sts());

        LeaveProposal done = handler.approve("bob", p.id()).block();

        assertEquals(LeaveProposal.Status.APPROVED, done.status());
        assertEquals("bob", done.decidedBy());
        Df leavingDf = dfs.get(DF);
        assertEquals(DfSts.LEAVING, leavingDf.sts());
        assertEquals("alice", leavingDf.leavingBy());
        assertEquals(NOW.plusSeconds(30L * 24 * 3600), leavingDf.leavesAt());
        assertEquals(1, mailed.size(), "the team is told");
        assertTrue(audited.stream().anyMatch(a -> a.startsWith("tenant.leaving") && a.contains("approvedBy=bob") && a.contains("proposedBy=alice")));
        assertTrue(audited.stream().anyMatch(a -> a.startsWith("tenant.end-contract-approved")));
    }

    @Test
    void aRejectionChangesNothingAndFreesTheNextProposal() {
        df(DfSts.ACTIVE);
        LeaveProposal p = handler.propose("alice", DF, Kind.START, "Reason").block();

        assertEquals(LeaveProposal.Status.REJECTED, handler.reject("bob", p.id(), " Not enough ").block().status());

        assertEquals(DfSts.ACTIVE, dfs.get(DF).sts());
        assertEquals("Not enough", stored.get(p.id()).decisionNote());
        assertEquals(LeaveProposal.Status.PENDING, handler.propose("carol", DF, Kind.START, "Again").block().status());
    }

    @Test
    void aDecisionIsFinal() {
        df(DfSts.ACTIVE);
        LeaveProposal p = handler.propose("alice", DF, Kind.START, "Reason").block();
        handler.approve("bob", p.id()).block();

        refused(409, "leave-proposal-decided", () -> handler.approve("carol", p.id()).block());
        refused(409, "leave-proposal-decided", () -> handler.reject("carol", p.id(), null).block());
        refused(409, "leave-proposal-decided", () -> handler.withdraw("alice", p.id()).block());
    }

    @Test
    void onlyOneProposalWaitsPerFiduciary() {
        df(DfSts.ACTIVE);
        handler.propose("alice", DF, Kind.START, "Reason").block();

        refused(409, "leave-proposal-pending", () -> handler.propose("carol", DF, Kind.START, "Other").block());
    }

    @Test
    void aReasonIsRequiredAndTheStatusMustFit() {
        df(DfSts.ACTIVE);
        refused(400, "invalid-request", () -> handler.propose("alice", DF, Kind.START, "  ").block());
        refused(409, "df-not-leaving", () -> handler.propose("alice", DF, Kind.CANCEL, "Reason").block());
        df(DfSts.PENDING);
        refused(409, "df-cannot-leave", () -> handler.propose("alice", DF, Kind.START, "Reason").block());
        df(DfSts.CLOSED);
        refused(409, "df-cannot-leave", () -> handler.propose("alice", DF, Kind.START, "Reason").block());
        refused(404, "df-not-found", () -> handler.propose("alice", "missing", Kind.START, "Reason").block());
    }

    @Test
    void onlyTheProposerWithdraws() {
        df(DfSts.ACTIVE);
        LeaveProposal p = handler.propose("alice", DF, Kind.START, "Reason").block();

        refused(403, "not-permitted", () -> handler.withdraw("bob", p.id()).block());
        assertEquals(LeaveProposal.Status.WITHDRAWN, handler.withdraw("alice", p.id()).block().status());
        assertEquals(DfSts.ACTIVE, dfs.get(DF).sts());
    }

    @Test
    void staffCancelAfterTwoPeopleAgreeEvenWhenTheOwnersCancellationIsUsed() {
        df(DfSts.ACTIVE);
        LeaveProposal start = handler.propose("alice", DF, Kind.START, "Reason").block();
        handler.approve("bob", start.id()).block();
        // the Owner used their one cancellation earlier
        dfs.get(DF).cancelLeaving();
        dfs.get(DF).startLeaving("owner-1", NOW, 30);
        assertFalse(dfs.get(DF).canCancelLeaving());

        LeaveProposal cancel = handler.propose("alice", DF, Kind.CANCEL, "Mistake, resolved").block();
        refused(403, "leave-proposal-same-person", () -> handler.approve("alice", cancel.id()).block());
        handler.approve("bob", cancel.id()).block();

        assertEquals(DfSts.ACTIVE, dfs.get(DF).sts());
        assertTrue(audited.stream().anyMatch(a -> a.startsWith("tenant.leaving-cancelled") && a.contains("approvedBy=bob")));
    }

    @Test
    void aFiduciaryThatChangedStandingBeforeApprovalFailsSafely() {
        df(DfSts.ACTIVE);
        LeaveProposal p = handler.propose("alice", DF, Kind.START, "Reason").block();
        df(DfSts.CLOSED);

        refused(409, "df-cannot-leave", () -> handler.approve("bob", p.id()).block());
    }
}
