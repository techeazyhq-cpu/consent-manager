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

package com.techeazyhq.cm.dsr.dom;

import java.time.Instant;
import java.util.Map;

import reactor.core.publisher.Mono;

/**
 * What a fiduciary is told when a request is relayed to it or withdrawn (ADR-047, ADR-058), written to the outbox in
 * the change's own transaction and delivered by the fiduciary's webhooks. Ids, the right and the deadline only: never
 * the principal's words or contact details, which the fiduciary reads through the API.
 */
public interface DpReqEvtPort {

    String RECEIVED = "principal_request.received";
    String WITHDRAWN = "principal_request.withdrawn";

    Mono<Void> publish(DfMessage message);

    record DfMessage(String id, String type, String dfId, Instant occurredAt, Map<String, Object> data) {
    }
}
