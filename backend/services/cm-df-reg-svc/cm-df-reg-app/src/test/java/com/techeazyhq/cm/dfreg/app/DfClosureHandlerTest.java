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

import com.techeazyhq.cm.dfreg.dom.ConsentsClosurePort;
import com.techeazyhq.cm.dfreg.dom.Df;
import com.techeazyhq.cm.dfreg.dom.DfFactory;
import com.techeazyhq.cm.dfreg.dom.DfLogo;
import com.techeazyhq.cm.dfreg.dom.DfLogoRepoPort;
import com.techeazyhq.cm.dfreg.dom.DfMember;
import com.techeazyhq.cm.dfreg.dom.DfMemberDirectoryPort;
import com.techeazyhq.cm.dfreg.dom.DfPrfl;
import com.techeazyhq.cm.dfreg.dom.DfRepoPort;
import com.techeazyhq.cm.dfreg.dom.DfRole;
import com.techeazyhq.cm.dfreg.dom.DfSts;
import com.techeazyhq.cm.dfreg.dom.NewDfMember;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class DfClosureHandlerTest {

    private static final Instant NOW = Instant.parse("2026-11-08T09:00:00Z");

    private final Map<String, Df> dfs = new LinkedHashMap<>();
    private final List<String> calls = new ArrayList<>();
    private final List<String> audited = new ArrayList<>();
    private final List<DfMember> team = new ArrayList<>(List.of(
            new DfMember("m1", "df-1", "owner@acme.example", "Owner", DfRole.OWNER),
            new DfMember("m2", "df-1", "dev@acme.example", "Dev", DfRole.DEVELOPER)));
    private boolean consentsFail;

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

    private final ConsentsClosurePort consents = dfId -> consentsFail ? Mono.error(new IllegalStateException("cns-cmd down"))
            : Mono.fromSupplier(() -> {
                calls.add("consents " + dfId);
                return new ConsentsClosurePort.Result(3, 1, 4);
            });

    private final DfMemberDirectoryPort members = new DfMemberDirectoryPort() {
        @Override
        public Mono<DfMember> invite(String dfId, NewDfMember member) {
            return Mono.empty();
        }

        @Override
        public Flux<DfMember> list(String dfId) {
            return Flux.fromIterable(List.copyOf(team));
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
            team.remove(member);
            calls.add("remove " + member.email());
            return Mono.empty();
        }
    };

    private final DfLogoRepoPort logos = new DfLogoRepoPort() {
        @Override
        public Mono<DfLogo> save(DfLogo logo) {
            return Mono.just(logo);
        }

        @Override
        public Mono<DfLogo> findByDfId(String dfId) {
            return Mono.empty();
        }

        @Override
        public Mono<Void> deleteByDfId(String dfId) {
            calls.add("logo " + dfId);
            return Mono.empty();
        }
    };

    private final InMemoryKeys keys = new InMemoryKeys();

    private boolean billingDown;

    private final com.techeazyhq.cm.dfreg.dom.BillingClosurePort billing = dfId -> {
        if (billingDown) {
            return Mono.error(new IllegalStateException("billing is down"));
        }
        calls.add("billing " + dfId);
        return Mono.empty();
    };

    private final DfClosureHandler handler = new DfClosureHandler(repo, consents, billing, keys, members, logos,
            (action, dfId, target, details) -> {
                audited.add(action + " " + details);
                return Mono.empty();
            }, Clock.fixed(NOW, ZoneOffset.UTC));

    private void df(String id, DfSts sts, Instant leavesAt) {
        Df df = DfFactory.rehydrate(id, "Acme Ltd", "ops@acme.example",
                new DfPrfl("Acme Limited", "+919876543210", "https://acme.example", "1 Road", "Retail", null, null, "Dee", "dpo@acme.example"),
                null, sts, NOW.minusSeconds(100_000), "secret", NOW.minusSeconds(90_000), null, null, DfSts.ACTIVE,
                NOW.minusSeconds(50_000), leavesAt, "owner-1", 0, null);
        dfs.put(id, df);
    }

    @Test
    void ifBillingCannotBeToldTheClosingIsTriedAgainAndNothingIsMarkedClosed() {
        df("df-1", DfSts.LEAVING, NOW);
        billingDown = true;

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> handler.cleanUp("df-1").block());
        assertEquals(DfSts.LEAVING, dfs.get("df-1").sts());
        assertTrue(!calls.contains("logo df-1"), "nothing after billing was done");

        billingDown = false;
        handler.cleanUp("df-1").block();
        assertTrue(calls.contains("billing df-1"));
    }

    @Test
    void onlyFiduciariesWhoseWindDownHasEndedAreDue() {
        df("ended", DfSts.LEAVING, NOW.minusSeconds(1));
        df("exactly-now", DfSts.LEAVING, NOW);
        df("later", DfSts.LEAVING, NOW.plusSeconds(60));
        df("active", DfSts.ACTIVE, null);
        df("closed", DfSts.CLOSED, NOW.minusSeconds(10));

        assertEquals(List.of("ended", "exactly-now"), handler.due().collectList().block());
    }

    @Test
    void cleanUpEndsConsentsRevokesKeysRemovesTheTeamAndLogo() {
        df("df-1", DfSts.LEAVING, NOW);
        keys.add("df-1", 2);
        keys.add("df-1", 1).revoke();
        keys.add("df-2", 1);

        DfClosureHandler.Summary s = handler.cleanUp("df-1").block();

        assertEquals(new DfClosureHandler.Summary(3, 1, 4, 2, 2), s);
        assertTrue(calls.contains("consents df-1"));
        assertTrue(calls.contains("remove owner@acme.example") && calls.contains("remove dev@acme.example"));
        assertTrue(calls.contains("logo df-1"));
        assertTrue(calls.contains("billing df-1"), "billing stops with the consents");
        assertTrue(team.isEmpty());
        assertEquals(0, keys.liveFor("df-1"));
        assertEquals(1, keys.liveFor("df-2"), "another fiduciary's keys are untouched");
    }

    @Test
    void finishClosesAndErasesTheProfileExceptWhatIsKept() {
        df("df-1", DfSts.LEAVING, NOW);

        Df closed = handler.finish("df-1", new DfClosureHandler.Summary(3, 1, 4, 2, 2)).block();

        assertEquals(DfSts.CLOSED, closed.sts());
        assertEquals(NOW, closed.closedAt());
        assertNull(closed.cmDfApiClientSecret(), "no token can be issued any more");
        assertEquals("[erased]", closed.contactEmail());
        assertNull(closed.prfl().contactPhone());
        assertNull(closed.prfl().website());
        assertNull(closed.prfl().registeredAddress());
        assertNull(closed.prfl().dpoEmail());
        assertEquals("Acme Limited", closed.prfl().legalName(), "the legal name is kept as the record of who it was");
        assertEquals("Acme Ltd", closed.name());
        assertEquals(1, audited.size());
        assertTrue(audited.get(0).startsWith("tenant.closed"));
        assertTrue(audited.get(0).contains("consentsEnded=3"));
    }

    @Test
    void aFailedStepLeavesItLeavingSoTheNextPassTriesAgain() {
        df("df-1", DfSts.LEAVING, NOW);
        consentsFail = true;

        assertThrows(IllegalStateException.class, () -> handler.cleanUp("df-1").block());

        assertEquals(DfSts.LEAVING, dfs.get("df-1").sts());
        assertTrue(team.size() == 2, "nothing after the failed step was done");
        consentsFail = false;
        assertEquals(3, handler.cleanUp("df-1").block().consentsEnded());
    }

    @Test
    void finishingTwiceOrOnAnythingNotLeavingDoesNothing() {
        df("df-1", DfSts.LEAVING, NOW);
        df("active", DfSts.ACTIVE, null);
        DfClosureHandler.Summary none = new DfClosureHandler.Summary(0, 0, 0, 0, 0);

        handler.finish("df-1", none).block();
        assertNull(handler.finish("df-1", none).block());
        assertNull(handler.finish("active", none).block());
        assertEquals(DfSts.ACTIVE, dfs.get("active").sts());
        assertEquals(1, audited.size());
    }

    @Test
    void closedIsFinal() {
        df("df-1", DfSts.LEAVING, NOW);
        Df closed = handler.finish("df-1", new DfClosureHandler.Summary(0, 0, 0, 0, 0)).block();

        assertThrows(IllegalStateException.class, () -> closed.startLeaving("x", NOW, 30));
        assertThrows(IllegalStateException.class, () -> closed.resume());
        assertThrows(IllegalStateException.class, () -> closed.cancelLeaving());
        assertThrows(IllegalStateException.class, () -> closed.close(NOW));
    }

    private static final class InMemoryKeys implements com.techeazyhq.cm.dfreg.dom.DfApiKeyRepoPort {
        final Map<String, com.techeazyhq.cm.dfreg.dom.DfApiKey> all = new LinkedHashMap<>();
        int seq;

        com.techeazyhq.cm.dfreg.dom.DfApiKey add(String dfId, int count) {
            com.techeazyhq.cm.dfreg.dom.DfApiKey last = null;
            for (int i = 0; i < count; i++) {
                String id = "k" + (++seq);
                last = com.techeazyhq.cm.dfreg.dom.DfApiKey.rehydrate(id, dfId, "key " + id, "cmk_" + id, "h" + id, "sealed", false,
                        NOW, "owner-1", null, null);
                all.put(id, last);
            }
            return last;
        }

        long liveFor(String dfId) {
            return all.values().stream().filter(k -> k.dfId().equals(dfId) && !k.isRevoked()).count();
        }

        @Override
        public Mono<com.techeazyhq.cm.dfreg.dom.DfApiKey> save(com.techeazyhq.cm.dfreg.dom.DfApiKey key) {
            all.put(key.id(), key);
            return Mono.just(key);
        }

        @Override
        public Mono<com.techeazyhq.cm.dfreg.dom.DfApiKey> findById(String id) {
            return Mono.justOrEmpty(all.get(id));
        }

        @Override
        public Mono<com.techeazyhq.cm.dfreg.dom.DfApiKey> findByHash(String keyHash) {
            return Mono.empty();
        }

        @Override
        public Flux<com.techeazyhq.cm.dfreg.dom.DfApiKey> findByDfId(String dfId) {
            return Flux.fromIterable(List.copyOf(all.values())).filter(k -> k.dfId().equals(dfId));
        }

        @Override
        public Mono<Void> touch(String id, Instant at) {
            return Mono.empty();
        }
    }
}
