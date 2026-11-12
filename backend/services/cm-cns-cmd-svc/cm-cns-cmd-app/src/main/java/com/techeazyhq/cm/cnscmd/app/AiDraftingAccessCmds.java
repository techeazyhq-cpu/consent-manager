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

import java.time.Clock;
import java.util.function.Function;
import java.util.regex.Pattern;

import com.techeazyhq.cm.audkit.AuditTrail;
import com.techeazyhq.cm.cnscmd.dom.AiDraftingAccess;
import com.techeazyhq.cm.cnscmd.dom.AiDraftingAccessRepoPort;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Who may draft purposes with the platform's model (ADR-114). Platform staff turn it on for a fiduciary at onboarding, or later when the fiduciary asks;
 * a fiduciary asks with an optional note and can withdraw while it waits; an administrator approves, or declines with a reason the fiduciary sees. Every
 * change is audited with who made it and for which fiduciary, and never holds anything a fiduciary typed into a draft.
 */
public class AiDraftingAccessCmds {

    private static final Pattern DF_ID = Pattern.compile("^[A-Za-z0-9-]{1,36}$");

    private final AiDraftingAccessRepoPort repo;
    private final AuditTrail audit;
    private final Clock clock;

    public AiDraftingAccessCmds(AiDraftingAccessRepoPort repo, AuditTrail audit, Clock clock) {
        this.repo = repo;
        this.audit = audit;
        this.clock = clock;
    }

    /** Whether a fiduciary may draft: the check the assistant makes before it calls the model. */
    public Mono<Boolean> isEnabled(String dfId) {
        return repo.find(dfId).map(AiDraftingAccess::enabled).defaultIfEmpty(false);
    }

    public Mono<AiDraftingAccess> get(String dfId) {
        return repo.find(dfId).defaultIfEmpty(AiDraftingAccess.none(dfId));
    }

    public Flux<AiDraftingAccess> pending() {
        return repo.pending();
    }

    public Flux<AiDraftingAccess> enabled() {
        return repo.enabled();
    }

    // ---- the fiduciary

    public Mono<AiDraftingAccess> ask(String dfId, String by, String note) {
        return change(dfId, a -> a.ask(by, note, clock.instant()), "ai-drafting.access-requested", by, null);
    }

    public Mono<AiDraftingAccess> withdraw(String dfId, String by) {
        return change(dfId, a -> a.withdraw(clock.instant()), "ai-drafting.access-withdrawn", by, null);
    }

    // ---- platform staff

    /** On for a fiduciary: at onboarding, directly, or by approving its request. */
    public Mono<AiDraftingAccess> grant(String dfId, String by) {
        return change(dfId, a -> a.grant(clock.instant()), "ai-drafting.access-granted", by, null);
    }

    public Mono<AiDraftingAccess> revoke(String dfId, String by) {
        return change(dfId, a -> a.revoke(clock.instant()), "ai-drafting.access-revoked", by, null);
    }

    /** Approving a waiting request turns it on. Refused when nothing waits, so an approval is always of a request. */
    public Mono<AiDraftingAccess> approve(String dfId, String by) {
        return change(dfId, a -> {
            if (a.view() != AiDraftingAccess.View.PENDING) {
                throw new IllegalStateException("There is no request waiting");
            }
            return a.grant(clock.instant());
        }, "ai-drafting.access-approved", by, null);
    }

    public Mono<AiDraftingAccess> decline(String dfId, String by, String reason) {
        return change(dfId, a -> a.decline(by, reason, clock.instant()), "ai-drafting.access-declined", by, reason == null ? null : "reasonGiven");
    }

    // ---- the one place a change is made, saved and recorded

    private Mono<AiDraftingAccess> change(String dfId, Function<AiDraftingAccess, AiDraftingAccess> rule, String action, String by, String extra) {
        if (dfId == null || !DF_ID.matcher(dfId).matches()) {
            return Mono.error(new DomRuleViolation(400, "invalid-ai-drafting-access", "Name the fiduciary"));
        }
        return get(dfId).flatMap(current -> {
            AiDraftingAccess next;
            try {
                next = rule.apply(current);
            } catch (IllegalStateException e) {
                return Mono.<AiDraftingAccess>error(new DomRuleViolation(409, "ai-drafting-access-state", e.getMessage()));
            } catch (IllegalArgumentException e) {
                return Mono.<AiDraftingAccess>error(new DomRuleViolation(400, "invalid-ai-drafting-access", e.getMessage()));
            }
            return repo.save(next).flatMap(saved -> audit.record(action, dfId, dfId, AuditTrail.details("by", by, "from", current.view().name(),
                    "to", saved.view().name(), "note", extra)).thenReturn(saved));
        });
    }
}
