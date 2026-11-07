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

import java.time.Instant;
import java.util.Map;

import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.client.api.SubscriptionInitialPosition;
import org.apache.pulsar.reactive.client.api.MessageResult;
import org.apache.pulsar.reactive.client.api.ReactivePulsarClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeazyhq.cm.cnscmd.dom.WhkMessage;
import com.techeazyhq.cm.cnscmd.dom.WhkRepoPort;
import com.techeazyhq.cm.evtkit.PulsarTopics;

import reactor.core.publisher.Mono;

/**
 * Hands the messages cm-dsr-svc writes for fiduciaries ({@code evt-df-message}, ADR-058) to their webhook endpoints,
 * the same deliveries and retries as a consent change's (ADR-047). Only the principal-request types are accepted; any
 * other is dropped with a warning, so no other service can send a fiduciary something in this service's name. A
 * redelivered message is no second delivery: deliveries are unique per endpoint and message id.
 */
public class PulsarDfMessageConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(PulsarDfMessageConsumer.class);
    private static final int CONCURRENCY = 4;

    private final ReactivePulsarClient pulsar;
    private final ObjectMapper objectMapper;
    private final WhkRepoPort webhooks;

    public PulsarDfMessageConsumer(ReactivePulsarClient pulsar, ObjectMapper objectMapper, WhkRepoPort webhooks) {
        this.pulsar = pulsar;
        this.objectMapper = objectMapper;
        this.webhooks = webhooks;
    }

    public void start() {
        String topic = PulsarTopics.evt("dsr", "df-message");
        pulsar.messageConsumer(Schema.STRING)
                .topic(topic)
                .subscriptionName(PulsarTopics.subscription("cnscmd", "webhooks-df-message"))
                .subscriptionInitialPosition(SubscriptionInitialPosition.Earliest)
                .build()
                .consumeMany(messages -> messages.flatMap(message -> handle(message.getValue())
                        .thenReturn(MessageResult.acknowledge(message.getMessageId(), message.getValue()))
                        .onErrorResume(e -> {
                            LOG.warn("Failed to queue a fiduciary message from {}, negatively acknowledging", topic, e);
                            return Mono.just(MessageResult.negativeAcknowledge(message.getMessageId(), message.getValue()));
                        }), CONCURRENCY))
                .subscribe();
    }

    Mono<Void> handle(String json) {
        return Mono.fromCallable(() -> objectMapper.readValue(json, DfMessage.class))
                .flatMap(m -> WhkMessage.relayed(m.id(), m.type(), m.dfId(), m.occurredAt(), m.data())
                        .map(webhooks::enqueue)
                        .orElseGet(() -> {
                            LOG.warn("Dropped a fiduciary message of type {}: not one cm-dsr-svc may send", m.type());
                            return Mono.empty();
                        }));
    }

    record DfMessage(String id, String type, String dfId, Instant occurredAt, Map<String, Object> data) {
    }
}
