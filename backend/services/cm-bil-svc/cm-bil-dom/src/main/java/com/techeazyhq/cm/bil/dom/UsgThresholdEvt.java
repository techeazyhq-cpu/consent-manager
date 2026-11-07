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

package com.techeazyhq.cm.bil.dom;

import java.time.Instant;
import java.util.UUID;

import com.techeazyhq.cm.domkit.DomainEvt;

/**
 * A fiduciary reached {@code threshold} percent of its monthly allowance (ADR-060). Consumed by cm-df-reg-svc, which
 * holds the fiduciary's contact address and tells it, and tells the platform's staff. Ids and numbers only: the
 * address never leaves cm-df-reg-svc.
 */
public record UsgThresholdEvt(UUID evtId, Instant occurredAt, String dfId, String period, int threshold, long used,
                              long quota, String planName, Instant resetsAt) implements DomainEvt {

    public static UsgThresholdEvt of(UsgAlert alert, Instant resetsAt) {
        return new UsgThresholdEvt(UUID.randomUUID(), alert.raisedAt(), alert.dfId(), alert.period(), alert.threshold(),
                alert.used(), alert.quota(), alert.planName(), resetsAt);
    }
}
