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

package com.techeazyhq.cm.cnscmd.infra;

import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.reactive.client.adapter.AdaptedReactivePulsarClientFactory;
import org.apache.pulsar.reactive.client.api.MessageSpec;
import org.apache.pulsar.reactive.client.api.ReactiveMessageSender;
import org.apache.pulsar.reactive.client.api.ReactiveMessageSenderCache;
import org.apache.pulsar.reactive.client.api.ReactivePulsarClient;

import reactor.core.publisher.Mono;

/**
 * Thin wrapper over a Pulsar Reactive {@link ReactiveMessageSender} (plan §6/§13 — user-requested
 * swap from Kafka). One sender per topic, built lazily and cached; {@code maxInflight} bounds
 * backpressure the same way a bounded queue would.
 *
 * <p>{@code .cache(...)} is not optional once {@code .maxInflight(...)} is set — the reactive
 * client throws {@code NullPointerException: cache must be provided when maxInflight is set}
 * without it. Found during Phase 1 verification: the Outbox relay silently never marked a
 * single event as published because every send attempt failed with this NPE.
 */
public class PulsarCnsEvtProducer {

    private final ReactivePulsarClient reactivePulsarClient;
    private final ReactiveMessageSenderCache senderCache = AdaptedReactivePulsarClientFactory.createCache();
    private final java.util.Map<String, ReactiveMessageSender<String>> senders = new java.util.concurrent.ConcurrentHashMap<>();

    public PulsarCnsEvtProducer(ReactivePulsarClient reactivePulsarClient) {
        this.reactivePulsarClient = reactivePulsarClient;
    }

    public Mono<Void> publish(String topic, String payloadJson) {
        return senderFor(topic).sendOne(MessageSpec.of(payloadJson)).then();
    }

    private ReactiveMessageSender<String> senderFor(String topic) {
        return senders.computeIfAbsent(topic, t -> reactivePulsarClient
                .messageSender(Schema.STRING)
                .topic(t)
                .cache(senderCache)
                .maxInflight(100)
                .build());
    }
}
