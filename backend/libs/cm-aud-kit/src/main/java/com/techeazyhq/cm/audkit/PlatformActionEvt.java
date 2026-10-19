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

package com.techeazyhq.cm.audkit;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.techeazyhq.cm.domkit.DomainEvt;

/**
 * Something someone did on the platform, for the audit ledger (ADR-040): {@code action} names it
 * ({@code plan-change.approved}), {@code ctx} is the bounded context that did it, {@code target} the thing acted on,
 * and {@code dfId} the fiduciary it concerns, if any. {@code details} are short facts for people reading the ledger,
 * never a data principal's personal data: plan names, roles, counts, a staff or member email address.
 */
public record PlatformActionEvt(UUID evtId, Instant occurredAt, String ctx, String action, String dfId, String target,
                                Actor actor, Map<String, String> details) implements DomainEvt {

    /** How the ledger tells these from the consent lifecycle's events: an action name has a dot. */
    public static boolean isAction(String evtType) {
        return evtType != null && evtType.contains(".");
    }

    public PlatformActionEvt {
        if (action == null || !isAction(action) || action.length() > 64) {
            throw new IllegalArgumentException("an action is noun.verb, at most 64 characters: " + action);
        }
        details = details == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(details));
    }

    public static PlatformActionEvt of(String ctx, String action, String dfId, String target, Actor actor,
                                       Map<String, String> details) {
        return new PlatformActionEvt(UUID.randomUUID(), Instant.now(), ctx, action, dfId, target, actor, details);
    }
}
