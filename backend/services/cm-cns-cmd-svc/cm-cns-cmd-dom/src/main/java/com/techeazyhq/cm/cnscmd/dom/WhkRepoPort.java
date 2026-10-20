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

package com.techeazyhq.cm.cnscmd.dom;

import java.time.Duration;
import java.time.Instant;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Webhook endpoints and deliveries (ADR-047): implemented by {@code R2dbcWhkRepoAdapter}. */
public interface WhkRepoPort {

    Mono<WhkEndpoint> saveEndpoint(WhkEndpoint endpoint);

    Mono<WhkEndpoint> findEndpoint(String dfId, String id);

    Flux<WhkEndpoint> endpoints(String dfId);

    /** Removes an endpoint and its deliveries. */
    Mono<Void> deleteEndpoint(String dfId, String id);

    /** Queues {@code msg} for every active endpoint of its fiduciary that wants it, in the caller's transaction. */
    Mono<Void> enqueue(WhkMessage msg);

    /** Queues {@code msg} for one endpoint, now. */
    Mono<WhkDelivery> enqueueFor(WhkEndpoint endpoint, WhkMessage msg);

    /**
     * Takes up to {@code limit} deliveries due by {@code now}, holding each for {@code lease}: a worker that dies
     * mid-send leaves it to be taken again once the lease ends, so it is sent at least once.
     */
    Flux<WhkDelivery> claimDue(Instant now, Duration lease, int limit);

    /** Records an attempt and the delivery's state after it. */
    Mono<Void> saveAttempt(WhkDelivery after, WhkDelivery.Attempt attempt);

    Mono<WhkDelivery> findDelivery(String dfId, String id);

    /** A fiduciary's deliveries, newest first, optionally of one endpoint or in one state. */
    Flux<WhkDelivery> deliveries(String dfId, String endpointId, WhkDelivery.Sts sts, int limit);

    Flux<WhkDelivery.Attempt> attempts(String deliveryId);

    Mono<WhkDelivery> saveDelivery(WhkDelivery delivery);
}
