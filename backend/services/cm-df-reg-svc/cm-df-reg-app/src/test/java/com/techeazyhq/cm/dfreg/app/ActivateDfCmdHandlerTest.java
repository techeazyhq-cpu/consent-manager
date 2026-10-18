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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.techeazyhq.cm.dfreg.dom.Df;
import com.techeazyhq.cm.dfreg.dom.DfApiClientProvisionPort;
import com.techeazyhq.cm.dfreg.dom.DfFactory;
import com.techeazyhq.cm.dfreg.dom.DfMember;
import com.techeazyhq.cm.dfreg.dom.DfMemberDirectoryPort;
import com.techeazyhq.cm.dfreg.dom.DfMemberExistsException;
import com.techeazyhq.cm.dfreg.dom.DfPrfl;
import com.techeazyhq.cm.dfreg.dom.DfRepoPort;
import com.techeazyhq.cm.dfreg.dom.DfRole;
import com.techeazyhq.cm.dfreg.dom.DfSts;
import com.techeazyhq.cm.dfreg.dom.NewDfMember;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class ActivateDfCmdHandlerTest {

    private final Map<String, Df> dfs = new LinkedHashMap<>();
    private final List<NewDfMember> invited = new ArrayList<>();
    private boolean inviteFails;

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

    private final DfMemberDirectoryPort members = new DfMemberDirectoryPort() {
        @Override
        public Mono<DfMember> invite(String dfId, NewDfMember member) {
            if (inviteFails) {
                return Mono.error(new DfMemberExistsException(member.email()));
            }
            invited.add(member);
            return Mono.just(new DfMember("u-1", dfId, member.email(), member.name(), member.role()));
        }

        @Override
        public Flux<DfMember> list(String dfId) {
            return Flux.empty();
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

    private final DfApiClientProvisionPort clients = new DfApiClientProvisionPort() {
        @Override
        public Mono<String> provisionClientSecret(String dfId) {
            return Mono.just("secret");
        }

        @Override
        public Mono<String> rotateClientSecret(String dfId) {
            return Mono.just("rotated");
        }
    };

    private final ActivateDfCmdHandler handler = new ActivateDfCmdHandler(dfRepo, clients, members);

    private void pendingDf(String legalName) {
        DfPrfl prfl = new DfPrfl(legalName, null, null, null, null, null, null, null, null);
        dfs.put("df-1", DfFactory.rehydrate("df-1", "Acme", "Ops@Acme.example", prfl, null, DfSts.PENDING,
                Instant.now(), null, null));
    }

    @Test
    void activationInvitesTheContactAsTheFirstOwner() {
        pendingDf("Acme (India) Private Limited");

        Df df = handler.handle(new ActivateDfCmd("df-1")).block();

        assertEquals(DfSts.ACTIVE, df.sts());
        assertEquals(1, invited.size());
        assertEquals("ops@acme.example", invited.get(0).email());
        assertEquals("Acme India Private Limited", invited.get(0).name());
        assertEquals(DfRole.OWNER, invited.get(0).role());
    }

    @Test
    void aFailedInvitationDoesntUndoActivation() {
        pendingDf(null);
        inviteFails = true;

        Df df = handler.handle(new ActivateDfCmd("df-1")).block();

        assertEquals(DfSts.ACTIVE, df.sts());
        assertTrue(invited.isEmpty());
    }
    // ---- sandbox keys (ADR-038), which reuse activation ----

    private static final String PROD_DF = "3f2b8c1e-0000-4000-8000-00000000abcd";
    private final IssueSandboxKeysHandler keys = new IssueSandboxKeysHandler(dfRepo, handler, clients);

    private IssueSandboxKeysHandler.Keys keysFor(DfRole role) {
        return keys.handle(new IssueSandboxKeysHandler.Cmd(PROD_DF, java.util.Optional.ofNullable(role), "Acme")).block();
    }

    @Test
    void theFirstKeysMakeAnActiveTwinOfTheFiduciary_laterOnesANewSecret() {
        IssueSandboxKeysHandler.Keys first = keysFor(DfRole.DEVELOPER);

        assertEquals(PROD_DF, first.dfId(), "the same id, so asking again finds it");
        assertEquals("df-api-" + PROD_DF, first.clientId());
        assertEquals("secret", first.clientSecret());
        assertTrue(first.created());
        Df twin = dfs.get(PROD_DF);
        assertEquals(DfSts.ACTIVE, twin.sts());
        assertEquals("Acme", twin.name());
        assertEquals("sandbox+3f2b8c1e@sandbox.example", twin.contactEmail(), "a test contact, not production's");
        assertEquals(DfFactory.SANDBOX_PLAN, twin.subPlnName());

        IssueSandboxKeysHandler.Keys again = keysFor(DfRole.OWNER);
        assertEquals("rotated", again.clientSecret());
        assertEquals(false, again.created());
        assertEquals("rotated", dfs.get(PROD_DF).cmDfApiClientSecret());
    }

    @Test
    void onlyPeopleWhoBuildIntegrationsTakeKeys() {
        for (DfRole role : new DfRole[] {DfRole.CONSENT_MANAGER, DfRole.AUDITOR, null}) {
            org.junit.jupiter.api.Assertions.assertThrows(com.techeazyhq.cm.webkit.DomRuleViolation.class,
                    () -> keysFor(role), String.valueOf(role));
        }
        org.junit.jupiter.api.Assertions.assertThrows(com.techeazyhq.cm.webkit.DomRuleViolation.class,
                () -> keys.handle(new IssueSandboxKeysHandler.Cmd(null, java.util.Optional.of(DfRole.OWNER), "Acme")).block());
        assertTrue(dfs.isEmpty());
    }
}
