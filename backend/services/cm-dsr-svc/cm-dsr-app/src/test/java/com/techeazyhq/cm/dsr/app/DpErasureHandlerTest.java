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

package com.techeazyhq.cm.dsr.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;

import com.techeazyhq.cm.audkit.AuditTrail;
import com.techeazyhq.cm.dsr.dom.DpCnsPort;
import com.techeazyhq.cm.dsr.dom.DpErasure;
import com.techeazyhq.cm.dsr.dom.DpErasure.Step;
import com.techeazyhq.cm.dsr.dom.DpErasurePorts;
import com.techeazyhq.cm.dsr.dom.DpReq;
import com.techeazyhq.cm.dsr.dom.DpReqEvtPort;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class DpErasureHandlerTest {

    /** A clock the test moves. */
    static final class TestClock extends Clock {
        Instant now = Instant.parse("2026-11-08T10:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final TestClock clock = new TestClock();
    private final InMemoryErasures erasures = new InMemoryErasures();
    private final List<String> sent = new ArrayList<>();
    private final List<List<String>> sentIds = new ArrayList<>();
    private final List<String> deleted = new ArrayList<>();
    private final List<String> audited = new ArrayList<>();
    private final List<DpReqEvtPort.DfMessage> messages = new ArrayList<>();
    private final DpReqHandlerTest.InMemoryReqs reqs = new DpReqHandlerTest.InMemoryReqs();
    private boolean identityDown;

    private final AuditTrail audit = (action, dfId, target, details) -> Mono.fromRunnable(() -> audited.add(action));
    private final DpCnsPort consents = (id, token) -> Mono.just(new DpCnsPort.DpCns(id, "df-1", "MARKETING"));
    private final DpReqHandler requests = new DpReqHandler(reqs, consents, m -> Mono.fromRunnable(() -> messages.add(m)),
            audit, DsrDeadlines.DPDPA, clock);
    private final DpErasureHandler handler = new DpErasureHandler(erasures,
            e -> Mono.fromRunnable(() -> {
                sent.add(e.step().name());
                sentIds.add(e.cnsReqIds());
            }),
            dpId -> identityDown ? Mono.error(new IllegalStateException("Keycloak is down"))
                    : Mono.fromRunnable(() -> deleted.add(dpId)),
            requests, audit, clock);

    private static final DsrCaller DP = new DsrCaller("dp-1", "p@example.com", "dp-1", null, false, false, "dp-1-token");
    private static final DsrCaller STAFF = new DsrCaller("s-1", "ops@cm", null, null, false, true, "t");

    private DpErasure start() {
        return handler.request(DP, "p@example.com", "close my account").block();
    }

    private void answer(DpErasure e, Step step, List<String> ids, int withdrawn) {
        handler.onStepDone(e.id(), step.name(), ids, withdrawn).block();
    }

    @Test
    void theStepsRunInOrderAndTheAccountGoesLast() {
        DpReq open = requests.relay(DP, "cns-1", "ACCESS", null).block();
        DpErasure e = start();
        assertEquals(List.of("NOTICE_AND_DEVICES"), sent);

        answer(e, Step.NOTICE_AND_DEVICES, null, 0);
        assertEquals(List.of("NOTICE_AND_DEVICES", "CONSENTS"), sent);

        answer(e, Step.CONSENTS, List.of("cns-1", "cns-2"), 2);
        assertEquals("AUDIT", sent.get(2));
        assertEquals(List.of("cns-1", "cns-2"), sentIds.get(2), "the audit step erases entries about these requests");
        assertTrue(deleted.isEmpty());

        answer(e, Step.AUDIT, null, 0);
        DpErasure done = erasures.findById(e.id()).block();
        assertEquals(DpErasure.Sts.DONE, done.sts());
        assertNull(done.email(), "the email is kept only while the saga runs");
        assertEquals(2, done.consentsWithdrawn());
        assertEquals(List.of("dp-1"), deleted);
        // the open request was withdrawn, its fiduciary told, and its words erased
        assertEquals(DpReq.Sts.WITHDRAWN, reqs.findById(open.id()).block().sts());
        assertEquals(DpReqEvtPort.WITHDRAWN, messages.get(messages.size() - 1).type());
        assertTrue(audited.contains("dp-erasure.requested") && audited.contains("dp-erasure.completed"));
    }

    @Test
    void aDuplicateOrLateAnswerChangesNothing() {
        DpErasure e = start();
        answer(e, Step.NOTICE_AND_DEVICES, null, 0);
        answer(e, Step.NOTICE_AND_DEVICES, null, 0);
        answer(e, Step.AUDIT, null, 0);
        assertEquals(List.of("NOTICE_AND_DEVICES", "CONSENTS"), sent);
        assertEquals(Step.CONSENTS, erasures.findById(e.id()).block().step());
    }

    @Test
    void itMustBeConfirmedAndOnlyOnceAtATime() {
        problem(400, "confirmation-required", handler.request(DP, "p@example.com", "yes"));
        problem(403, "not-a-data-principal", handler.request(STAFF, null, "CLOSE MY ACCOUNT"));
        start();
        problem(409, "erasure-running", handler.request(DP, "p@example.com", "CLOSE MY ACCOUNT"));
    }

    @Test
    void aStepWithoutAnAnswerIsSentAgainThenStopsForStaff() {
        DpErasure e = start();
        handler.redrive().block();
        assertEquals(1, sent.size(), "not yet due");

        for (int i = 1; i < DpErasure.MAX_ATTEMPTS; i++) {
            clock.now = clock.now.plus(Duration.ofMinutes(3));
            handler.redrive().block();
        }
        assertEquals(DpErasure.MAX_ATTEMPTS, sent.size());
        clock.now = clock.now.plus(Duration.ofMinutes(3));
        handler.redrive().block();
        DpErasure stuck = erasures.findById(e.id()).block();
        assertEquals(DpErasure.Sts.STUCK, stuck.sts());
        assertTrue(audited.contains("dp-erasure.stuck"));

        problem(403, "not-platform-staff", handler.retry(DP, e.id()));
        assertEquals(DpErasure.Sts.RUNNING, handler.retry(STAFF, e.id()).block().sts());
        assertEquals(DpErasure.MAX_ATTEMPTS + 1, sent.size(), "a retry sends the step again at once");
    }

    @Test
    void aLocalStepThatFailsIsTriedAgain() {
        identityDown = true;
        DpErasure e = start();
        answer(e, Step.NOTICE_AND_DEVICES, null, 0);
        answer(e, Step.CONSENTS, List.of(), 0);
        answer(e, Step.AUDIT, null, 0);
        DpErasure waiting = erasures.findById(e.id()).block();
        assertEquals(Step.IDENTITY, waiting.step());
        assertTrue(waiting.lastError().contains("Keycloak is down"));

        identityDown = false;
        clock.now = clock.now.plus(Duration.ofMinutes(3));
        handler.redrive().block();
        assertEquals(DpErasure.Sts.DONE, erasures.findById(e.id()).block().sts());
        assertEquals(List.of("dp-1"), deleted);
    }

    private static void problem(int status, String type, Mono<?> call) {
        try {
            call.block();
            fail("expected " + type);
        } catch (DomRuleViolation e) {
            assertEquals(status, e.httpStatus(), e.getMessage());
            assertEquals(type, e.problemType());
        }
    }

    /** Keeps the rule of {@code uq_dp_erasure_running}. */
    static final class InMemoryErasures implements DpErasurePorts.Repo {

        private final Map<String, DpErasure> rows = new ConcurrentHashMap<>();

        @Override
        public Mono<DpErasure> save(DpErasure e) {
            boolean clash = e.sts() != DpErasure.Sts.DONE && rows.values().stream()
                    .anyMatch(r -> !r.id().equals(e.id()) && r.dpId().equals(e.dpId()) && r.sts() != DpErasure.Sts.DONE);
            if (clash) {
                return Mono.error(new DpErasurePorts.ErasureRunningException());
            }
            // stored as a copy, as a database would
            rows.put(e.id(), copy(e));
            return Mono.just(e);
        }

        @Override
        public Mono<DpErasure> findById(String id) {
            return Mono.justOrEmpty(rows.get(id)).map(InMemoryErasures::copy);
        }

        @Override
        public Mono<DpErasure> findLatestByDpId(String dpId) {
            return Flux.fromIterable(rows.values()).filter(r -> r.dpId().equals(dpId))
                    .sort(Comparator.comparing(DpErasure::requestedAt).reversed()).next().map(InMemoryErasures::copy);
        }

        @Override
        public Flux<DpErasure> findRunning(int limit) {
            return Flux.fromIterable(rows.values()).filter(r -> r.sts() == DpErasure.Sts.RUNNING).map(InMemoryErasures::copy);
        }

        @Override
        public Flux<DpErasure> findBySts(DpErasure.Sts sts, int limit) {
            return Flux.fromIterable(rows.values()).filter(r -> sts == null || r.sts() == sts).map(InMemoryErasures::copy);
        }

        private static DpErasure copy(DpErasure e) {
            return DpErasure.rehydrate(e.id(), e.dpId(), e.email(), e.requestedAt(), e.sts(), e.step(), e.attempts(),
                    e.dispatchedAt(), e.finishedAt(), e.lastError(), e.cnsReqIds(), e.consentsWithdrawn());
        }
    }
}
