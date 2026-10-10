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

package com.techeazyhq.cm.bil.infra;

import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.client.api.SubscriptionInitialPosition;
import org.apache.pulsar.reactive.client.api.MessageResult;
import org.apache.pulsar.reactive.client.api.ReactivePulsarClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeazyhq.cm.bil.app.OnDfActivatedEvtHandler;
import com.techeazyhq.cm.evtkit.PulsarTopics;

import reactor.core.publisher.Mono;

/** Choreography (plan §7c): auto-creates a FREE DfSub whenever cm-df-reg-svc activates a DF. */
public class PulsarDfActivatedConsumer {

    private final ReactivePulsarClient reactivePulsarClient;
    private final ObjectMapper objectMapper;
    private final OnDfActivatedEvtHandler handler;

    public PulsarDfActivatedConsumer(ReactivePulsarClient reactivePulsarClient, ObjectMapper objectMapper,
                                      OnDfActivatedEvtHandler handler) {
        this.reactivePulsarClient = reactivePulsarClient;
        this.objectMapper = objectMapper;
        this.handler = handler;
    }

    public void start() {
        String topic = PulsarTopics.evt("dfreg", "df-activated");
        reactivePulsarClient.messageConsumer(Schema.STRING)
                .topic(topic)
                .subscriptionName(PulsarTopics.subscription("dfreg", "bil-on-activated-sub"))
                // Default is Latest, which makes a brand-new durable subscription miss every
                // message published before it first connects — wrong for an idempotent
                // choreography handler (evtId-deduped) that should catch up on everything.
                // Discovered during Phase 1 verification: cm-bil-svc's first-ever subscribe
                // silently missed the DfActivatedEvt from the very activation that triggered it.
                .subscriptionInitialPosition(SubscriptionInitialPosition.Earliest)
                .build()
                .consumeMany(messageFlux -> messageFlux.flatMap(message -> {
                    try {
                        JsonNode node = objectMapper.readTree(message.getValue());
                        return handler.handle(node.get("dfId").asText())
                                .thenReturn(MessageResult.acknowledge(message.getMessageId(), message.getValue()));
                    } catch (Exception e) {
                        org.slf4j.LoggerFactory.getLogger(PulsarDfActivatedConsumer.class)
                                .warn("Failed to handle DfActivatedEvt, negatively acknowledging", e);
                        return Mono.just(MessageResult.negativeAcknowledge(message.getMessageId(), message.getValue()));
                    }
                }))
                .subscribe();
    }
}
