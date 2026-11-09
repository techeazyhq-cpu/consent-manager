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

import java.time.Instant;

import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.client.api.SubscriptionInitialPosition;
import org.apache.pulsar.reactive.client.api.MessageResult;
import org.apache.pulsar.reactive.client.api.ReactivePulsarClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeazyhq.cm.dfreg.app.OnInvoiceReminderEvtHandler;
import com.techeazyhq.cm.evtkit.PulsarTopics;

import reactor.core.publisher.Mono;

/**
 * Reads cm-bil-svc's {@code bil/evt-invoice-reminder} (ADR-105) and has {@link OnInvoiceReminderEvtHandler} email the fiduciary. A message
 * that can't be read is dropped; one that fails to send is redelivered.
 */
public class PulsarInvoiceReminderConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(PulsarInvoiceReminderConsumer.class);
    private static final int CONCURRENCY = 2;

    private final ReactivePulsarClient pulsar;
    private final ObjectMapper objectMapper;
    private final OnInvoiceReminderEvtHandler handler;

    public PulsarInvoiceReminderConsumer(ReactivePulsarClient pulsar, ObjectMapper objectMapper, OnInvoiceReminderEvtHandler handler) {
        this.pulsar = pulsar;
        this.objectMapper = objectMapper;
        this.handler = handler;
    }

    public void start() {
        pulsar.messageConsumer(Schema.STRING)
                .topic(PulsarTopics.evt("bil", "invoice-reminder"))
                .subscriptionName(PulsarTopics.subscription("dfreg", "invoice-reminders"))
                .subscriptionInitialPosition(SubscriptionInitialPosition.Earliest)
                .build()
                .consumeMany(messages -> messages.flatMap(message -> Mono
                        .fromCallable(() -> objectMapper.readValue(message.getValue(), Payload.class))
                        .flatMap(p -> handler.handle(new OnInvoiceReminderEvtHandler.Reminder(p.dfId(), p.invoiceId(), p.number(),
                                p.totalMinor(), p.currency(), p.dueAt(), p.stepDays())))
                        .thenReturn(MessageResult.acknowledge(message.getMessageId(), message.getValue()))
                        .onErrorResume(com.fasterxml.jackson.core.JsonProcessingException.class, e -> {
                            LOG.warn("Dropped an unreadable invoice reminder: {}", e.getOriginalMessage());
                            return Mono.just(MessageResult.acknowledge(message.getMessageId(), message.getValue()));
                        })
                        .onErrorResume(e -> {
                            LOG.warn("Failed to send an invoice reminder, negatively acknowledging", e);
                            return Mono.just(MessageResult.negativeAcknowledge(message.getMessageId(), message.getValue()));
                        }), CONCURRENCY))
                .subscribe();
    }

    record Payload(String dfId, String invoiceId, String number, long totalMinor, String currency, Instant dueAt, int stepDays) {
    }
}
