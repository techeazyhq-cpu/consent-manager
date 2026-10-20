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

package com.techeazyhq.cm.cnscmd.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.techeazyhq.cm.audkit.AuditTrail;
import com.techeazyhq.cm.cnscmd.dom.SecretSealerPort;
import com.techeazyhq.cm.cnscmd.dom.WhkDelivery;
import com.techeazyhq.cm.cnscmd.dom.WhkEndpoint;
import com.techeazyhq.cm.cnscmd.dom.WhkMessage;
import com.techeazyhq.cm.cnscmd.dom.WhkRepoPort;
import com.techeazyhq.cm.cnscmd.dom.WhkSenderPort;
import com.techeazyhq.cm.cnscmd.dom.WhkSignature;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Endpoints are a fiduciary's own; deliveries are signed, retried, dead-lettered and replayed (ADR-047). */
class WebhooksTest {

    private static final Instant T0 = Instant.parse("2026-10-20T00:00:00Z");
    private static final List<Duration> DELAYS = List.of(Duration.ofMinutes(1));

    private final InMemoryWhkRepo repo = new InMemoryWhkRepo();
    private final FakeSender sender = new FakeSender();
    private final SecretSealerPort sealer = new SecretSealerPort() {
        @Override
        public String seal(String secret) {
            return "sealed:" + secret;
        }

        @Override
        public String open(String sealed) {
            return sealed.substring("sealed:".length());
        }
    };
    private final List<String> audited = new ArrayList<>();
    private final AuditTrail audit = (action, dfId, target, details) -> Mono.fromRunnable(() -> audited.add(action));
    private final Clock clock = Clock.fixed(T0, ZoneOffset.UTC);
    private final Webhooks webhooks = new Webhooks(repo, sender, sealer, audit, clock, false);
    private final WhkDeliveryWorker worker = new WhkDeliveryWorker(repo, sender, sealer, clock, DELAYS);

    @Test
    void aNewEndpointShowsItsSecretOnceAndKeepsItSealed() {
        Webhooks.WithSecret made = webhooks.create("df-1", " https://df.example/h ", "orders", Set.of()).block();

        assertTrue(made.secret().startsWith("whsec_"));
        assertEquals("https://df.example/h", made.endpoint().url());
        assertEquals("sealed:" + made.secret(), repo.endpoints.get(made.endpoint().id()).secretSealed());
        assertEquals(List.of("webhook.created"), audited);
    }

    @Test
    void refusesBadUrlsInternalHostsAndTooMany() {
        assertStatus(400, webhooks.create("df-1", "http://df.example/h", null, Set.of()));
        sender.refuse = true;
        assertStatus(400, webhooks.create("df-1", "https://internal.example/h", null, Set.of()));
        sender.refuse = false;
        for (int i = 0; i < WhkEndpoint.MAX_PER_DF; i++) {
            webhooks.create("df-1", "https://df.example/" + i, null, Set.of()).block();
        }
        assertStatus(409, webhooks.create("df-1", "https://df.example/11", null, Set.of()));
        webhooks.create("df-2", "https://other.example/h", null, Set.of()).block();
    }

    @Test
    void anotherFiduciarysEndpointIsNotFound() {
        String id = webhooks.create("df-1", "https://df.example/h", null, Set.of()).block().endpoint().id();
        assertStatus(404, webhooks.endpoint("df-2", id));
        assertStatus(404, webhooks.rotateSecret("df-2", id));
        assertStatus(404, webhooks.delete("df-2", id));
        assertStatus(404, webhooks.sendTest("df-2", id));
        assertEquals(1, repo.endpoints.size());
    }

    @Test
    void deliversSignedAndRetriesThenDeadLettersThenReplays() {
        Webhooks.WithSecret made = webhooks.create("df-1", "https://df.example/h", null, Set.of()).block();
        repo.enqueue(new WhkMessage("evt-1", "consent.given", "df-1", T0, Map.of("cnsReqId", "req-1"))).block();

        sender.status = 503;
        assertEquals(1L, worker.pass().block());
        WhkDelivery d = repo.deliveries.values().iterator().next();
        assertEquals(WhkDelivery.Sts.PENDING, d.sts());
        assertEquals(T0.plus(Duration.ofMinutes(1)), d.nextAttemptAt());
        Map<String, String> headers = sender.sent.get(0);
        assertEquals("evt-1", headers.get("webhook-id"));
        assertEquals(WhkSignature.header(List.of(made.secret()), "evt-1", T0.getEpochSecond(), d.payload()),
                headers.get("webhook-signature"));

        assertEquals(0L, worker.pass().block(), "not due until its retry");
        repo.deliveries.put(d.id(), d.replayed(T0));
        assertEquals(1L, worker.pass().block());
        WhkDelivery dead = repo.deliveries.get(d.id());
        assertEquals(WhkDelivery.Sts.FAILED, dead.sts());
        assertEquals(2, repo.attempts.get(d.id()).size());

        sender.status = 200;
        WhkDelivery replayed = webhooks.replay("df-1", d.id()).block();
        assertStatus(409, webhooks.replay("df-1", d.id()));
        assertEquals(1L, worker.pass().block());
        assertEquals(WhkDelivery.Sts.DELIVERED, repo.deliveries.get(d.id()).sts());
        assertEquals("evt-1", sender.sent.get(2).get("webhook-id"), "the same id on a replay");
        assertEquals(WhkDelivery.Sts.PENDING, replayed.sts());
        assertTrue(audited.contains("webhook.replayed"));
        assertStatus(404, webhooks.replay("df-2", d.id()));
    }

    @Test
    void aRotationSignsWithBothSecretsForADay() {
        Webhooks.WithSecret made = webhooks.create("df-1", "https://df.example/h", null, Set.of()).block();
        Webhooks.WithSecret rotated = webhooks.rotateSecret("df-1", made.endpoint().id()).block();
        assertNotEquals(made.secret(), rotated.secret());

        webhooks.sendTest("df-1", made.endpoint().id()).block();
        worker.pass().block();

        String sigs = sender.sent.get(0).get("webhook-signature");
        assertEquals(2, sigs.split(" ").length);
        assertTrue(audited.contains("webhook.secret-rotated"));
    }

    @Test
    void onlySubscribedActiveEndpointsGetAMessage() {
        String given = webhooks.create("df-1", "https://df.example/given", null, Set.of("consent.given")).block()
                .endpoint().id();
        String off = webhooks.create("df-1", "https://df.example/all", null, Set.of()).block().endpoint().id();
        webhooks.setActive("df-1", off, false).block();

        repo.enqueue(new WhkMessage("evt-1", "consent.withdrawn", "df-1", T0, Map.of())).block();
        repo.enqueue(new WhkMessage("evt-2", "consent.given", "df-1", T0, Map.of())).block();

        assertEquals(1, repo.deliveries.size());
        assertEquals(given, repo.deliveries.values().iterator().next().endpointId());
        assertStatus(409, webhooks.sendTest("df-1", off));
    }

    @Test
    void aDisabledOrDeletedEndpointCountsAsAFailedAttempt() {
        String id = webhooks.create("df-1", "https://df.example/h", null, Set.of()).block().endpoint().id();
        webhooks.sendTest("df-1", id).block();
        webhooks.delete("df-1", id).block();

        worker.pass().block();

        assertTrue(sender.sent.isEmpty());
        WhkDelivery d = repo.deliveries.values().iterator().next();
        assertEquals("the endpoint was deleted", d.lastError());
        assertFalse(repo.endpoints.containsKey(id));
    }

    @Test
    void listsDeliveriesWithAttempts() {
        String id = webhooks.create("df-1", "https://df.example/h", null, Set.of()).block().endpoint().id();
        WhkDelivery test = webhooks.sendTest("df-1", id).block();
        sender.status = 204;
        worker.pass().block();

        assertEquals(1, webhooks.deliveries("df-1", null, WhkDelivery.Sts.DELIVERED, 0).collectList().block().size());
        assertEquals(0, webhooks.deliveries("df-2", null, null, 50).collectList().block().size());
        Webhooks.DeliveryView view = webhooks.delivery("df-1", test.id()).block();
        assertEquals(1, view.attempts().size());
        assertEquals(204, view.attempts().get(0).status());
        assertStatus(404, webhooks.delivery("df-2", test.id()));
    }

    private static void assertStatus(int status, Mono<?> mono) {
        DomRuleViolation e = assertThrows(DomRuleViolation.class, mono::block);
        assertEquals(status, e.httpStatus());
    }

    static final class FakeSender implements WhkSenderPort {
        final List<Map<String, String>> sent = new ArrayList<>();
        int status = 200;
        boolean refuse;

        @Override
        public Mono<Result> post(String url, Map<String, String> headers, String body) {
            sent.add(new LinkedHashMap<>(headers));
            return Mono.just(new Result(status, null));
        }

        @Override
        public Mono<Optional<String>> refusal(String url) {
            return Mono.just(refuse ? Optional.of("private") : Optional.empty());
        }
    }

    static final class InMemoryWhkRepo implements WhkRepoPort {
        final Map<String, WhkEndpoint> endpoints = new LinkedHashMap<>();
        final Map<String, WhkDelivery> deliveries = new LinkedHashMap<>();
        final Map<String, List<WhkDelivery.Attempt>> attempts = new LinkedHashMap<>();

        @Override
        public Mono<WhkEndpoint> saveEndpoint(WhkEndpoint e) {
            endpoints.put(e.id(), e);
            return Mono.just(e);
        }

        @Override
        public Mono<WhkEndpoint> findEndpoint(String dfId, String id) {
            return Mono.justOrEmpty(endpoints.get(id)).filter(e -> e.dfId().equals(dfId));
        }

        @Override
        public Flux<WhkEndpoint> endpoints(String dfId) {
            return Flux.fromIterable(List.copyOf(endpoints.values())).filter(e -> e.dfId().equals(dfId));
        }

        @Override
        public Mono<Void> deleteEndpoint(String dfId, String id) {
            return Mono.fromRunnable(() -> endpoints.remove(id));
        }

        @Override
        public Mono<Void> enqueue(WhkMessage msg) {
            return Flux.fromIterable(List.copyOf(endpoints.values()))
                    .filter(e -> e.dfId().equals(msg.dfId()) && e.wants(msg.type()))
                    .flatMap(e -> enqueueFor(e, msg)).then();
        }

        @Override
        public Mono<WhkDelivery> enqueueFor(WhkEndpoint endpoint, WhkMessage msg) {
            return saveDelivery(new WhkDelivery(UUID.randomUUID().toString(), endpoint.id(), endpoint.dfId(), msg.id(),
                    msg.type(), msg.body().toString(), WhkDelivery.Sts.PENDING, 0, T0, null, null, null, T0));
        }

        @Override
        public Flux<WhkDelivery> claimDue(Instant now, Duration lease, int limit) {
            List<WhkDelivery> due = deliveries.values().stream()
                    .filter(d -> d.sts() == WhkDelivery.Sts.PENDING && !d.nextAttemptAt().isAfter(now))
                    .sorted(Comparator.comparing(WhkDelivery::nextAttemptAt)).limit(limit).toList();
            return Flux.fromIterable(due);
        }

        @Override
        public Mono<Void> saveAttempt(WhkDelivery after, WhkDelivery.Attempt attempt) {
            attempts.computeIfAbsent(after.id(), k -> new ArrayList<>()).add(attempt);
            return saveDelivery(after).then();
        }

        @Override
        public Mono<WhkDelivery> findDelivery(String dfId, String id) {
            return Mono.justOrEmpty(deliveries.get(id)).filter(d -> d.dfId().equals(dfId));
        }

        @Override
        public Flux<WhkDelivery> deliveries(String dfId, String endpointId, WhkDelivery.Sts sts, int limit) {
            return Flux.fromIterable(List.copyOf(deliveries.values()))
                    .filter(d -> d.dfId().equals(dfId) && (endpointId == null || d.endpointId().equals(endpointId))
                            && (sts == null || d.sts() == sts))
                    .take(limit);
        }

        @Override
        public Flux<WhkDelivery.Attempt> attempts(String deliveryId) {
            return Flux.fromIterable(attempts.getOrDefault(deliveryId, List.of()));
        }

        @Override
        public Mono<WhkDelivery> saveDelivery(WhkDelivery d) {
            deliveries.put(d.id(), d);
            return Mono.just(d);
        }
    }
}
