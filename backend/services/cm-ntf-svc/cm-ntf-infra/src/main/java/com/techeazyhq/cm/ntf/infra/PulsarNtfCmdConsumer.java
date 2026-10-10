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

package com.techeazyhq.cm.ntf.infra;

import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.client.api.SubscriptionInitialPosition;
import org.apache.pulsar.reactive.client.api.MessageResult;
import org.apache.pulsar.reactive.client.api.ReactivePulsarClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeazyhq.cm.ntf.app.NotifyDfCmd;
import com.techeazyhq.cm.ntf.app.NotifyDfCmdHandler;
import com.techeazyhq.cm.ntf.app.NotifyDpCmd;
import com.techeazyhq.cm.ntf.app.NotifyDpCmdHandler;
import com.techeazyhq.cm.ntf.dom.NtfDeliveryRejectedException;

import reactor.core.publisher.Mono;

/**
 * Subscribes to cm-cns-cmd-svc's {@code cm.ntf.cmd.notify-dp.v1}/{@code notify-df.v1} topics
 * (plan §7c, §13) and dispatches to the app-layer handlers. {@code start()} is called once
 * from the boot module's composition root, not self-triggered via an annotation, to keep
 * startup ordering explicit.
 */
public class PulsarNtfCmdConsumer {

    private final ReactivePulsarClient reactivePulsarClient;
    private final ObjectMapper objectMapper;
    private final NotifyDpCmdHandler notifyDpCmdHandler;
    private final NotifyDfCmdHandler notifyDfCmdHandler;

    public PulsarNtfCmdConsumer(ReactivePulsarClient reactivePulsarClient, ObjectMapper objectMapper,
                                 NotifyDpCmdHandler notifyDpCmdHandler, NotifyDfCmdHandler notifyDfCmdHandler) {
        this.reactivePulsarClient = reactivePulsarClient;
        this.objectMapper = objectMapper;
        this.notifyDpCmdHandler = notifyDpCmdHandler;
        this.notifyDfCmdHandler = notifyDfCmdHandler;
    }

    public void start() {
        consume("persistent://cm/ntf/cmd-notify-dp-v1", "ntf-notify-dp-sub", json -> {
            NotifyDpCmd cmd = readValue(json, NotifyDpCmd.class);
            return notifyDpCmdHandler.handle(cmd);
        });
        consume("persistent://cm/ntf/cmd-notify-df-v1", "ntf-notify-df-sub", json -> {
            NotifyDfCmd cmd = readValue(json, NotifyDfCmd.class);
            return notifyDfCmdHandler.handle(cmd);
        });
    }

    private void consume(String topic, String subscriptionName, java.util.function.Function<String, Mono<Void>> handler) {
        reactivePulsarClient.messageConsumer(Schema.STRING)
                .topic(topic)
                .subscriptionName(subscriptionName)
                // Default is Latest — same race as the other consumers (see
                // PulsarDfActivatedConsumer's comment): a command published just before this
                // consumer's first-ever subscribe would otherwise be silently missed.
                .subscriptionInitialPosition(SubscriptionInitialPosition.Earliest)
                .build()
                .consumeMany(messageFlux -> messageFlux.flatMap(message ->
                        handler.apply(message.getValue())
                                .thenReturn(MessageResult.acknowledge(message.getMessageId(), message.getValue()))
                                .onErrorResume(NtfDeliveryRejectedException.class, err -> {
                                    // Refused for good (bad recipient, channel not allowed, out of credit): a
                                    // redelivery would be refused the same way, so drop it rather than loop.
                                    org.slf4j.LoggerFactory.getLogger(PulsarNtfCmdConsumer.class)
                                            .warn("Delivery rejected for message on {}, acknowledging: {}", topic, err.getMessage());
                                    return Mono.just(MessageResult.acknowledge(message.getMessageId(), message.getValue()));
                                })
                                .onErrorResume(err -> {
                                    org.slf4j.LoggerFactory.getLogger(PulsarNtfCmdConsumer.class)
                                            .warn("Failed to handle message on {}, negatively acknowledging", topic, err);
                                    return Mono.just(MessageResult.negativeAcknowledge(message.getMessageId(), message.getValue()));
                                })))
                .subscribe();
    }

    private <T> T readValue(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalArgumentException("Malformed payload on notify-cmd topic", e);
        }
    }
}
