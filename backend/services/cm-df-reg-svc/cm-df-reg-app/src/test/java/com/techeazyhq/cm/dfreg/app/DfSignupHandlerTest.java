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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.techeazyhq.cm.dfreg.dom.Df;
import com.techeazyhq.cm.dfreg.dom.DfFactory;
import com.techeazyhq.cm.dfreg.dom.DfPrfl;
import com.techeazyhq.cm.dfreg.dom.DfRepoPort;
import com.techeazyhq.cm.dfreg.dom.DfSignup;
import com.techeazyhq.cm.dfreg.dom.DfSignupRepoPort;
import com.techeazyhq.cm.dfreg.dom.DfSts;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class DfSignupHandlerTest {

    private static final Instant NOW = Instant.parse("2026-11-08T09:00:00Z");

    private final AtomicReference<Instant> clockNow = new AtomicReference<>(NOW);
    private final Clock clock = new Clock() {
        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return clockNow.get();
        }
    };

    private final Map<String, DfSignup> signups = new LinkedHashMap<>();
    private final Map<String, Df> dfs = new LinkedHashMap<>();
    private final List<String> mails = new ArrayList<>();
    private final List<String> bodies = new ArrayList<>();
    private final List<String> audited = new ArrayList<>();
    private boolean mailFails;

    private final DfSignupRepoPort signupRepo = new DfSignupRepoPort() {
        @Override
        public Mono<DfSignup> save(DfSignup s) {
            signups.put(s.id(), s);
            return Mono.just(s);
        }

        @Override
        public Mono<DfSignup> findById(String id) {
            return Mono.justOrEmpty(signups.get(id));
        }

        @Override
        public Mono<Long> countByEmailSince(String email, Instant since) {
            return Mono.just(signups.values().stream().filter(s -> s.contactEmail().equals(email) && !s.createdAt().isBefore(since)).count());
        }

        @Override
        public Mono<Long> countSince(Instant since) {
            return Mono.just(signups.values().stream().filter(s -> !s.createdAt().isBefore(since)).count());
        }

        @Override
        public Mono<Long> deleteUnconfirmedExpiredBefore(Instant before) {
            long n = signups.values().stream().filter(s -> s.status() == DfSignup.Status.AWAITING_EMAIL && s.expiresAt().isBefore(before)).count();
            signups.values().removeIf(s -> s.status() == DfSignup.Status.AWAITING_EMAIL && s.expiresAt().isBefore(before));
            return Mono.just(n);
        }
    };

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
            return Flux.fromIterable(List.copyOf(dfs.values()));
        }
    };

    private final DfSignupHandler handler = new DfSignupHandler(signupRepo, dfRepo,
            (to, subject, body, ref) -> mailFails ? Mono.error(new IllegalStateException("mail down")) : Mono.fromRunnable(() -> {
                mails.add(to + ": " + subject);
                bodies.add(body);
            }), (action, dfId, target, details) -> {
                audited.add(action + " " + details);
                return Mono.empty();
            }, clock, "http://console.test/", 3, 5, Duration.ofHours(24), List.of("staff@cm.example"));

    private static DfSignupHandler.Application app(String name, String email) {
        return new DfSignupHandler.Application(name, email, "Acme Limited", "Retail", "https://acme.example");
    }

    private static void refused(int status, String type, Runnable action) {
        DomRuleViolation v = assertThrows(DomRuleViolation.class, action::run);
        assertEquals(status, v.httpStatus());
        assertEquals(type, v.problemType());
    }

    private String lastToken() {
        Matcher m = Pattern.compile("token=([A-Za-z0-9_-]+)").matcher(bodies.get(bodies.size() - 1));
        assertTrue(m.find(), "the mail carries a link with a token");
        return m.group(1);
    }

    private String lastId() {
        Matcher m = Pattern.compile("id=([0-9a-f-]{36})").matcher(bodies.get(bodies.size() - 1));
        assertTrue(m.find());
        return m.group(1);
    }

    @Test
    void anApplicationMailsALinkAndCreatesNoFiduciaryYet() {
        handler.apply(app("Acme Ltd", "Owner@Acme.example")).block();

        assertEquals(1, signups.size());
        assertTrue(dfs.isEmpty(), "nothing is created until the address is proven");
        assertEquals("owner@acme.example: Confirm your Consent Manager application", mails.get(0));
        assertTrue(bodies.get(0).contains("http://console.test/#/signup/verify?id="), "no double slash from the configured url");
        assertFalse(signups.values().iterator().next().tokenHash().contains(lastToken()), "only a hash of the token is kept");
    }

    @Test
    void confirmingCreatesAPendingFiduciaryMarkedSelfServiceAndTellsBothSides() {
        handler.apply(app("Acme Ltd", "owner@acme.example")).block();

        handler.verify(lastId(), lastToken()).block();

        Df df = dfs.values().iterator().next();
        assertEquals(DfSts.PENDING, df.sts());
        assertEquals("Acme Ltd", df.name());
        assertEquals("owner@acme.example", df.contactEmail());
        assertEquals("Acme Limited", df.prfl().legalName());
        assertEquals(NOW, df.signedUpAt());
        assertNotNull(df.id());
        assertEquals(DfSignup.Status.VERIFIED, signups.values().iterator().next().status());
        assertTrue(audited.get(0).startsWith("tenant.signed-up"));
        assertTrue(mails.contains("owner@acme.example: We received your application to Consent Manager"));
        assertTrue(mails.contains("staff@cm.example: New application: Acme Ltd"));
    }

    @Test
    void aLinkWorksOnceOnlyAndOnlyWithItsOwnToken() {
        handler.apply(app("Acme Ltd", "owner@acme.example")).block();
        String id = lastId();
        String token = lastToken();

        refused(400, "signup-invalid-token", () -> handler.verify(id, "wrong-token").block());
        refused(400, "signup-invalid-token", () -> handler.verify("no-such-id", token).block());
        refused(400, "signup-invalid-token", () -> handler.verify(id, null).block());
        assertTrue(dfs.isEmpty());

        handler.verify(id, token).block();
        refused(400, "signup-invalid-token", () -> handler.verify(id, token).block());
        assertEquals(1, dfs.size());
    }

    @Test
    void aLinkExpires() {
        handler.apply(app("Acme Ltd", "owner@acme.example")).block();
        String id = lastId();
        String token = lastToken();
        clockNow.set(NOW.plus(Duration.ofHours(25)));

        refused(400, "signup-invalid-token", () -> handler.verify(id, token).block());
        assertTrue(dfs.isEmpty());
    }

    @Test
    void anAddressAlreadyAFiduciaryIsToldOnlyToWhoProvesTheMailbox() {
        dfs.put("existing", DfFactory.rehydrate("existing", "Old Co", "OWNER@acme.example", DfPrfl.EMPTY, null, DfSts.ACTIVE, NOW, "s", NOW));
        handler.apply(app("Acme Ltd", "owner@acme.example")).block();
        assertEquals(1, mails.size(), "applying gives the same answer and mail as for an unknown address");

        refused(409, "df-already-registered", () -> handler.verify(lastId(), lastToken()).block());
        assertEquals(1, dfs.size());
    }

    @Test
    void anAddressIsHeldToThreeApplicationsADay() {
        for (int i = 0; i < 3; i++) {
            handler.apply(app("Acme " + i, "owner@acme.example")).block();
        }

        refused(429, "signup-rate-limited", () -> handler.apply(app("Acme 4", "owner@acme.example")).block());
        handler.apply(app("Other", "other@example.test")).block();

        clockNow.set(NOW.plus(Duration.ofHours(25)));
        handler.apply(app("Acme 5", "owner@acme.example")).block();
    }

    @Test
    void thePlatformIsHeldToADailyCap() {
        for (int i = 0; i < 5; i++) {
            handler.apply(app("Org " + i, "person" + i + "@example.test")).block();
        }

        refused(429, "signup-rate-limited", () -> handler.apply(app("Org 6", "late@example.test")).block());
    }

    @Test
    void anInvalidApplicationIsRefusedBeforeAnythingIsKept() {
        refused(400, "invalid-df-profile", () -> handler.apply(app(" ", "owner@acme.example")).block());
        refused(400, "invalid-df-profile", () -> handler.apply(app("Acme", "not-an-address")).block());
        refused(400, "invalid-df-profile", () -> handler.apply(new DfSignupHandler.Application("Acme", "a@b.example", null, null, "ftp://nope")).block());

        assertTrue(signups.isEmpty() && mails.isEmpty());
    }

    @Test
    void aMailFailureToStaffDoesNotUndoTheApplication() {
        handler.apply(app("Acme Ltd", "owner@acme.example")).block();
        String id = lastId();
        String token = lastToken();
        mailFails = true;

        handler.verify(id, token).block();

        assertEquals(1, dfs.size());
    }

    @Test
    void staffRejectAWaitingApplicationAndTheApplicantIsToldWhy() {
        handler.apply(app("Acme Ltd", "owner@acme.example")).block();
        handler.verify(lastId(), lastToken()).block();
        String dfId = dfs.keySet().iterator().next();

        Df rejected = handler.reject(dfId, "  Could not verify the registration number ").block();

        assertEquals(DfSts.REJECTED, rejected.sts());
        assertEquals("Could not verify the registration number", rejected.rejectionReason());
        assertEquals(NOW, rejected.rejectedAt());
        assertTrue(mails.contains("owner@acme.example: About your application to Consent Manager"));
        assertTrue(bodies.get(bodies.size() - 1).contains("Could not verify the registration number"));
        assertTrue(audited.stream().anyMatch(a -> a.startsWith("tenant.rejected")));
    }

    @Test
    void onlyAWaitingApplicationCanBeRejectedAndItNeedsAReason() {
        dfs.put("active", DfFactory.rehydrate("active", "A", "a@a.example", DfPrfl.EMPTY, null, DfSts.ACTIVE, NOW, "s", NOW));
        dfs.put("pending", DfFactory.register("P", "p@p.example", DfPrfl.EMPTY, null));
        String pendingId = dfs.get("pending").id();
        dfs.put(pendingId, dfs.remove("pending"));

        refused(409, "df-cannot-reject", () -> handler.reject("active", "Reason").block());
        refused(400, "invalid-request", () -> handler.reject(pendingId, "  ").block());
        refused(404, "df-not-found", () -> handler.reject("missing", "Reason").block());
        assertEquals(DfSts.PENDING, dfs.get(pendingId).sts());
    }

    @Test
    void unconfirmedApplicationsAreDeletedAWeekAfterTheirLinkExpires() {
        handler.apply(app("Acme Ltd", "owner@acme.example")).block();
        clockNow.set(NOW.plus(Duration.ofHours(24 + 24 * 6)));
        assertEquals(0L, handler.purgeStale().block(), "six days after expiry: kept");

        clockNow.set(NOW.plus(Duration.ofHours(24 + 24 * 8)));
        assertEquals(1L, handler.purgeStale().block());
        assertTrue(signups.isEmpty());
    }
}
