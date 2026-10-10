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

package com.techeazyhq.cm.domkit;

import java.time.Instant;
import java.util.UUID;

/**
 * Marker for a domain event raised by an {@link Agg}. {@code evtId} is the idempotency key
 * every Pulsar consumer in this platform dedupes on (at-least-once delivery, §7c of the plan).
 */
public interface DomainEvt {
    UUID evtId();

    Instant occurredAt();
}
