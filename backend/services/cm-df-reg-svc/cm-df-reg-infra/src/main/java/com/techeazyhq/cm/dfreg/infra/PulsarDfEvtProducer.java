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

package com.techeazyhq.cm.dfreg.infra;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.reactive.client.adapter.AdaptedReactivePulsarClientFactory;
import org.apache.pulsar.reactive.client.api.MessageSpec;
import org.apache.pulsar.reactive.client.api.ReactiveMessageSender;
import org.apache.pulsar.reactive.client.api.ReactiveMessageSenderCache;
import org.apache.pulsar.reactive.client.api.ReactivePulsarClient;

import reactor.core.publisher.Mono;

/** See cm-cns-cmd-infra's PulsarCnsEvtProducer for why {@code .cache(...)} is required alongside {@code .maxInflight(...)}. */
public class PulsarDfEvtProducer {

    private final ReactivePulsarClient reactivePulsarClient;
    private final ReactiveMessageSenderCache senderCache = AdaptedReactivePulsarClientFactory.createCache();
    private final Map<String, ReactiveMessageSender<String>> senders = new ConcurrentHashMap<>();

    public PulsarDfEvtProducer(ReactivePulsarClient reactivePulsarClient) {
        this.reactivePulsarClient = reactivePulsarClient;
    }

    public Mono<Void> publish(String topic, String payloadJson) {
        return senderFor(topic).sendOne(MessageSpec.of(payloadJson)).then();
    }

    private ReactiveMessageSender<String> senderFor(String topic) {
        return senders.computeIfAbsent(topic, t -> reactivePulsarClient.messageSender(Schema.STRING).topic(t).cache(senderCache).maxInflight(100).build());
    }
}
