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

package com.techeazyhq.cm.stf.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.techeazyhq.cm.stf.dom.NewOperator;
import com.techeazyhq.cm.stf.dom.StfAcct;
import com.techeazyhq.cm.stf.dom.StfAcctExistsException;
import com.techeazyhq.cm.stf.dom.StfDirectoryPort;
import com.techeazyhq.cm.stf.dom.StfRole;
import com.techeazyhq.cm.stf.dom.StfSession;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Who may manage platform staff, and what can't be done to whom. */
class StfRulesTest {

    private static final StfCaller ADMIN = new StfCaller("admin-1", true);
    private static final StfCaller OPERATOR = new StfCaller("op-1", false);

    private final Map<String, StfAcct> accounts = new LinkedHashMap<>(Map.of(
            "admin-1", acct("admin-1", "admin@cm.local", "Ada", StfRole.CM_ADMIN),
            "admin-2", acct("admin-2", "admin2@cm.local", "Bo", StfRole.CM_ADMIN),
            "op-1", acct("op-1", "op@cm.local", "Cy", StfRole.CM_OPERATOR)));
    private final InMemoryDirectory directory = new InMemoryDirectory();

    @Test
    void anAdministratorAddsAnOperatorWhoCanSignInWithTheirEmail() {
        StfAcct created = new CreateOperatorCmdHandler(directory)
                .handle(new CreateOperatorCmdHandler.Cmd(ADMIN, " Dee@Example.COM ", " Dee Rao ")).block();

        assertEquals("dee@example.com", created.email());
        assertEquals("Dee Rao", created.name());
        assertEquals(StfRole.CM_OPERATOR, created.role());
    }

    @Test
    void anOperatorCannotAddStaff() {
        assertEquals("admin-only", refusal(() -> new CreateOperatorCmdHandler(directory)
                .handle(new CreateOperatorCmdHandler.Cmd(OPERATOR, "dee@example.com", "Dee")).block()).problemType());
    }

    @Test
    void anAddressThatAlreadyHasAnAccountIsA409() {
        DomRuleViolation refused = refusal(() -> new CreateOperatorCmdHandler(directory)
                .handle(new CreateOperatorCmdHandler.Cmd(ADMIN, "op@cm.local", "Again")).block());

        assertEquals(409, refused.httpStatus());
        assertEquals("account-exists", refused.problemType());
    }

    @Test
    void aBadEmailOrNameIsA400() {
        assertEquals("invalid-operator", refusal(() -> new CreateOperatorCmdHandler(directory)
                .handle(new CreateOperatorCmdHandler.Cmd(ADMIN, "not-an-email", "Dee")).block()).problemType());
        assertEquals("invalid-operator", refusal(() -> new CreateOperatorCmdHandler(directory)
                .handle(new CreateOperatorCmdHandler.Cmd(ADMIN, "dee@example.com", " ")).block()).problemType());
    }

    @Test
    void anAdministratorDisablesAnOperatorAndTheyAreSignedOut() {
        StfAcct disabled = new SetStfEnabledCmdHandler(directory)
                .handle(new SetStfEnabledCmdHandler.Cmd(ADMIN, "op-1", false)).block();

        assertFalse(disabled.enabled());
        assertFalse(accounts.get("op-1").enabled());
        assertTrue(directory.signedOut.contains("op-1"));
    }

    @Test
    void nobodyDisablesThemselvesOrAnAdministratorOrAnyoneAsAnOperator() {
        SetStfEnabledCmdHandler handler = new SetStfEnabledCmdHandler(directory);

        assertEquals("cannot-change-self",
                refusal(() -> handler.handle(new SetStfEnabledCmdHandler.Cmd(ADMIN, "admin-1", false)).block()).problemType());
        assertEquals("cannot-change-admin",
                refusal(() -> handler.handle(new SetStfEnabledCmdHandler.Cmd(ADMIN, "admin-2", false)).block()).problemType());
        assertEquals("admin-only",
                refusal(() -> handler.handle(new SetStfEnabledCmdHandler.Cmd(OPERATOR, "op-1", false)).block()).problemType());
        assertEquals("staff-not-found",
                refusal(() -> handler.handle(new SetStfEnabledCmdHandler.Cmd(ADMIN, "nobody", false)).block()).problemType());
        assertTrue(accounts.values().stream().allMatch(StfAcct::enabled));
    }

    @Test
    void theStaffListIsForAdministratorsAndPutsThemFirst() {
        List<String> names = new ListStfQryHandler(directory).handle(ADMIN).map(StfAcct::name).collectList().block();

        assertEquals(List.of("Ada", "Bo", "Cy"), names);
        assertEquals("admin-only",
                refusal(() -> new ListStfQryHandler(directory).handle(OPERATOR).collectList().block()).problemType());
    }

    @Test
    void anyoneOnStaffSeesTheirOwnAccount() {
        assertEquals("op@cm.local", new MyAcctHandler(directory).profile(OPERATOR).block().email());
    }

    private static DomRuleViolation refusal(Runnable call) {
        return assertThrows(DomRuleViolation.class, call::run);
    }

    private static StfAcct acct(String id, String email, String name, StfRole role) {
        return new StfAcct(id, email, name, role, true, Instant.parse("2026-10-01T00:00:00Z"));
    }

    private final class InMemoryDirectory implements StfDirectoryPort {

        private final java.util.Set<String> signedOut = new java.util.HashSet<>();

        @Override
        public Mono<StfAcct> createOperator(NewOperator operator) {
            if (accounts.values().stream().anyMatch(a -> a.email().equals(operator.email()))) {
                return Mono.error(new StfAcctExistsException(operator.email()));
            }
            StfAcct acct = acct("op-" + accounts.size(), operator.email(), operator.name(), StfRole.CM_OPERATOR);
            accounts.put(acct.id(), acct);
            return Mono.just(acct);
        }

        @Override
        public Flux<StfAcct> listStaff() {
            return Flux.fromIterable(List.copyOf(accounts.values())).sort((a, b) -> b.name().compareTo(a.name()));
        }

        @Override
        public Mono<StfAcct> findStaff(String id) {
            return Mono.justOrEmpty(accounts.get(id));
        }

        @Override
        public Mono<Void> setEnabled(String id, boolean enabled) {
            return Mono.fromRunnable(() -> {
                StfAcct a = accounts.get(id);
                accounts.put(id, new StfAcct(a.id(), a.email(), a.name(), a.role(), enabled, a.createdAt()));
                if (!enabled) {
                    signedOut.add(id);
                }
            });
        }

        @Override
        public Flux<StfSession> sessionsOf(String id) {
            return Flux.empty();
        }

        @Override
        public Mono<Void> signOutEverywhere(String id) {
            return Mono.fromRunnable(() -> signedOut.add(id));
        }
    }
}
