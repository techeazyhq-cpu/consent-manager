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
import static org.junit.jupiter.api.Assertions.assertNull;
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
import com.techeazyhq.cm.dfreg.dom.NewDfMember;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class DfLeavingHandlerTest {

    private static final Instant NOW = Instant.parse("2026-11-08T09:00:00Z");
    private static final String DF = "df-1";

    private final Map<String, Df> dfs = new LinkedHashMap<>();
    private final List<String> audited = new ArrayList<>();
    private final List<String> mailed = new ArrayList<>();
    private boolean mailFails;

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

    private final DfMemberDirectoryPort team = new DfMemberDirectoryPort() {
        @Override
        public Mono<DfMember> invite(String dfId, NewDfMember member) {
            return Mono.empty();
        }

        @Override
        public Flux<DfMember> list(String dfId) {
            return Flux.just(new DfMember("m1", dfId, "owner@acme.example", "Owner", DfRole.OWNER),
                    new DfMember("m2", dfId, "dev@acme.example", "Dev", DfRole.DEVELOPER));
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

    private final DfLeavingHandler handler = new DfLeavingHandler(repo, team,
            (to, subject, body, ref) -> mailFails ? Mono.error(new IllegalStateException("mail down")) : Mono.fromRunnable(() -> mailed.add(to + ": " + subject)),
            (action, dfId, target, details) -> {
                audited.add(action + " " + details);
                return Mono.empty();
            }, Clock.fixed(NOW, ZoneOffset.UTC), 30);

    private static DfTeamCaller owner() {
        return DfTeamCaller.member("owner-1", DF, DfRole.OWNER);
    }

    private void df(DfSts sts) {
        dfs.put(DF, DfFactory.rehydrate(DF, "Acme Ltd", "ops@acme.example", DfPrfl.EMPTY, null, sts, NOW, "secret", NOW));
    }

    private static void refused(int status, String type, Runnable action) {
        DomRuleViolation refusal = assertThrows(DomRuleViolation.class, action::run);
        assertEquals(status, refusal.httpStatus());
        assertEquals(type, refusal.problemType());
    }

    @Test
    void theOwnerLeavesByTypingTheNameAndAWindDownBegins() {
        df(DfSts.ACTIVE);

        Df leaving = handler.leave(owner(), "  acme ltd ").block();

        assertEquals(DfSts.LEAVING, leaving.sts());
        assertEquals(NOW, leaving.leavingAt());
        assertEquals(NOW.plusSeconds(30L * 24 * 3600), leaving.leavesAt());
        assertEquals("owner-1", leaving.leavingBy());
        assertTrue(leaving.canCancelLeaving());
        assertTrue(audited.get(0).startsWith("tenant.leaving"));
        assertEquals(2, mailed.size(), "the whole team is told");
        assertTrue(mailed.get(0).startsWith("owner@acme.example"));
    }

    @Test
    void theWrongNameOrNoNameDoesNotStartIt() {
        df(DfSts.ACTIVE);

        refused(400, "invalid-request", () -> handler.leave(owner(), "Acme").block());
        refused(400, "invalid-request", () -> handler.leave(owner(), null).block());
        assertEquals(DfSts.ACTIVE, dfs.get(DF).sts());
        assertTrue(audited.isEmpty() && mailed.isEmpty());
    }

    @Test
    void onlyAnOwnerLeavesAndNotStaff() {
        df(DfSts.ACTIVE);

        for (DfRole role : List.of(DfRole.ADMIN, DfRole.CONSENT_MANAGER, DfRole.DEVELOPER, DfRole.AUDITOR)) {
            refused(403, "not-permitted", () -> handler.leave(DfTeamCaller.member("u", DF, role), "Acme Ltd").block());
        }
        refused(403, "not-permitted", () -> handler.leave(DfTeamCaller.staff("s"), "Acme Ltd").block());
        assertEquals(DfSts.ACTIVE, dfs.get(DF).sts());
    }

    @Test
    void aPendingOrAlreadyLeavingFiduciaryCannotLeave() {
        df(DfSts.PENDING);
        refused(409, "df-cannot-leave", () -> handler.leave(owner(), "Acme Ltd").block());

        df(DfSts.ACTIVE);
        handler.leave(owner(), "Acme Ltd").block();
        refused(409, "df-cannot-leave", () -> handler.leave(owner(), "Acme Ltd").block());
    }

    @Test
    void aSuspendedFiduciaryCanLeaveAndCancellingReturnsItToSuspended() {
        df(DfSts.SUSPENDED);

        handler.leave(owner(), "Acme Ltd").block();
        Df back = handler.cancel(owner()).block();

        assertEquals(DfSts.SUSPENDED, back.sts());
        assertNull(back.leavesAt());
    }

    @Test
    void cancellingOnceRestoresItAndASecondDepartureCannotBeCancelled() {
        df(DfSts.ACTIVE);
        handler.leave(owner(), "Acme Ltd").block();

        Df back = handler.cancel(owner()).block();

        assertEquals(DfSts.ACTIVE, back.sts());
        assertNull(back.leavingAt());
        assertFalse(back.canCancelLeaving());
        assertTrue(audited.stream().anyMatch(a -> a.startsWith("tenant.leaving-cancelled")));

        Df again = handler.leave(owner(), "Acme Ltd").block();
        assertFalse(again.canCancelLeaving(), "the one cancellation is used");
        refused(409, "leave-cancel-used", () -> handler.cancel(owner()).block());
        assertEquals(DfSts.LEAVING, dfs.get(DF).sts());
    }

    @Test
    void thereIsNothingToCancelWhenNotLeaving() {
        df(DfSts.ACTIVE);

        refused(409, "df-not-leaving", () -> handler.cancel(owner()).block());
        refused(403, "not-permitted", () -> handler.cancel(DfTeamCaller.member("u", DF, DfRole.ADMIN)).block());
    }

    @Test
    void aMailFailureDoesNotStopTheDeparture() {
        df(DfSts.ACTIVE);
        mailFails = true;

        Df leaving = handler.leave(owner(), "Acme Ltd").block();

        assertEquals(DfSts.LEAVING, leaving.sts());
        assertEquals(DfSts.LEAVING, dfs.get(DF).sts());
    }

    @Test
    void anyMemberSeesTheStatus() {
        df(DfSts.ACTIVE);
        handler.leave(owner(), "Acme Ltd").block();

        assertEquals(DfSts.LEAVING, handler.status(DfTeamCaller.member("a", DF, DfRole.AUDITOR)).block().sts());
        refused(403, "not-permitted", () -> handler.status(DfTeamCaller.staff("s")).block());
    }
}
