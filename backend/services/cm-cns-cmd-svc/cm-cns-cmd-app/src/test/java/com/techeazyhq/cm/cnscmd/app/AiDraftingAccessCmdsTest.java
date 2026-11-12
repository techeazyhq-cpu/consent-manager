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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeazyhq.cm.cnscmd.dom.AiDraftingAccess;
import com.techeazyhq.cm.cnscmd.dom.AiDraftingAccessRepoPort;
import com.techeazyhq.cm.cnscmd.dom.DraftingModelPort;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Turning drafting on for a fiduciary at onboarding or when it asks, and the assistant's check of it (ADR-114). */
class AiDraftingAccessCmdsTest {

    private final Map<String, AiDraftingAccess> rows = new LinkedHashMap<>();
    private final List<String> audited = new ArrayList<>();
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-12T10:00:00Z"), ZoneOffset.UTC);

    private final AiDraftingAccessRepoPort repo = new AiDraftingAccessRepoPort() {
        @Override
        public Mono<AiDraftingAccess> find(String dfId) {
            return Mono.justOrEmpty(rows.get(dfId));
        }

        @Override
        public Mono<AiDraftingAccess> save(AiDraftingAccess a) {
            rows.put(a.dfId(), a);
            return Mono.just(a);
        }

        @Override
        public Flux<AiDraftingAccess> pending() {
            return Flux.fromIterable(rows.values()).filter(a -> a.view() == AiDraftingAccess.View.PENDING);
        }

        @Override
        public Flux<AiDraftingAccess> enabled() {
            return Flux.fromIterable(rows.values()).filter(AiDraftingAccess::enabled);
        }
    };

    private final AiDraftingAccessCmds cmds = new AiDraftingAccessCmds(repo,
            (action, dfId, target, details) -> Mono.fromRunnable(() -> audited.add(action + " " + dfId + " " + details)), clock);

    private static void refused(int status, String type, Runnable r) {
        DomRuleViolation v = assertThrows(DomRuleViolation.class, r::run);
        assertEquals(status, v.httpStatus());
        assertEquals(type, v.problemType());
    }

    @Test
    void notEnabledUntilStaffTurnItOn() {
        assertFalse(cmds.isEnabled("df-1").block());
        cmds.grant("df-1", "op-1").block();
        assertTrue(cmds.isEnabled("df-1").block());
        assertTrue(audited.get(0).startsWith("ai-drafting.access-granted df-1") && audited.get(0).contains("by=op-1") && audited.get(0).contains("to=ENABLED"));
    }

    @Test
    void askingThenApprovingTurnsItOn() {
        cmds.ask("df-1", "owner-1", "we draft a lot").block();
        assertEquals(1, cmds.pending().collectList().block().size());
        assertFalse(cmds.isEnabled("df-1").block(), "asking is not having");
        cmds.approve("df-1", "admin-1").block();
        assertTrue(cmds.isEnabled("df-1").block());
        assertEquals(0, cmds.pending().collectList().block().size());
        assertEquals(1, cmds.enabled().collectList().block().size());
    }

    @Test
    void decliningKeepsItOffAndSaysWhy() {
        cmds.ask("df-1", "owner-1", null).block();
        cmds.decline("df-1", "admin-1", "Trial accounts do not get it").block();
        assertFalse(cmds.isEnabled("df-1").block());
        assertEquals("Trial accounts do not get it", cmds.get("df-1").block().request().declineReason());
        assertTrue(audited.get(1).contains("ai-drafting.access-declined"));
    }

    @Test
    void anApprovalIsOfARequest() {
        refused(409, "ai-drafting-access-state", () -> cmds.approve("df-1", "admin-1").block());
        assertFalse(cmds.isEnabled("df-1").block());
    }

    @Test
    void askingTwiceOrWhenItIsOnIsAConflict() {
        cmds.ask("df-1", "o", null).block();
        refused(409, "ai-drafting-access-state", () -> cmds.ask("df-1", "o", null).block());
        cmds.grant("df-2", "a").block();
        refused(409, "ai-drafting-access-state", () -> cmds.ask("df-2", "o", null).block());
    }

    @Test
    void aDeclineWithoutAReasonAndABadIdAreRefused() {
        cmds.ask("df-1", "o", null).block();
        refused(400, "invalid-ai-drafting-access", () -> cmds.decline("df-1", "a", " ").block());
        refused(400, "invalid-ai-drafting-access", () -> cmds.grant("../etc", "a").block());
        refused(400, "invalid-ai-drafting-access", () -> cmds.grant(null, "a").block());
    }

    @Test
    void withdrawAndRevoke() {
        cmds.ask("df-1", "o", null).block();
        cmds.withdraw("df-1", "o").block();
        assertEquals(0, cmds.pending().collectList().block().size());
        cmds.grant("df-1", "a").block();
        cmds.revoke("df-1", "a").block();
        assertFalse(cmds.isEnabled("df-1").block());
    }

    @Test
    void theAuditTrailNeverHoldsTheNoteOrReason() {
        cmds.ask("df-1", "o", "a secret-ish note").block();
        cmds.decline("df-1", "a", "a private reason").block();
        assertTrue(audited.stream().noneMatch(x -> x.contains("secret-ish") || x.contains("private reason")), audited.toString());
    }

    @Test
    void theAssistantRefusesAFiduciaryWithoutAccessBeforeCallingTheModel() {
        int[] calls = {0};
        DraftingModelPort model = new DraftingModelPort() {
            @Override
            public Status status() {
                return new Status(true, "m");
            }

            @Override
            public Mono<String> complete(String system, String user) {
                calls[0]++;
                return Mono.just("{}");
            }
        };
        PrpsDraftAssist assist = new PrpsDraftAssist(model, (a, d, t, x) -> Mono.empty(), new ObjectMapper(), clock, 2000, 20, 2, cmds::isEnabled);
        String ask = "We email customers about where their order is, using their email address and phone number.";
        refused(403, "ai-drafting-not-enabled", () -> assist.draft("df-1", "u", ask, "en").block());
        assertEquals(0, calls[0], "the model is never called for a fiduciary without access");
        cmds.grant("df-1", "a").block();
        assertThrows(DomRuleViolation.class, () -> assist.draft("df-1", "u", ask, "en").block()); // now past the gate; the empty reply is unusable
        assertTrue(calls[0] > 0);
    }
}
