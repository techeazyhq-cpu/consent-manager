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

package com.techeazyhq.cm.dfreg.dom;

import java.time.Instant;
import java.util.UUID;

import com.techeazyhq.cm.domkit.DomainEvt;

/**
 * Consumed by cm-bil-svc to subscribe the fiduciary (plan §7c, §11), to {@code subPlnName}, the plan chosen at
 * onboarding; null (events from before plans were chosen) means FREE.
 */
public record DfActivatedEvt(UUID evtId, Instant occurredAt, String dfId, String name, String subPlnName)
        implements DomainEvt {

    public static DfActivatedEvt of(String dfId, String name, String subPlnName) {
        return new DfActivatedEvt(UUID.randomUUID(), Instant.now(), dfId, name, subPlnName);
    }
}
