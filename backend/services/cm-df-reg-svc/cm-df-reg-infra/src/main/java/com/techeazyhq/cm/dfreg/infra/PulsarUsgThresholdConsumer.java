package com.techeazyhq.cm.dfreg.infra;

import java.time.Instant;

import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.client.api.SubscriptionInitialPosition;
import org.apache.pulsar.reactive.client.api.MessageResult;
import org.apache.pulsar.reactive.client.api.ReactivePulsarClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeazyhq.cm.dfreg.app.OnUsgThresholdEvtHandler;
import com.techeazyhq.cm.evtkit.PulsarTopics;

import reactor.core.publisher.Mono;

/**
 * Reads cm-bil-svc's {@code bil/evt-usg-threshold} (ADR-060) and has {@link OnUsgThresholdEvtHandler} email the
 * fiduciary and the platform. A message that can't be read is dropped; one that fails to send is redelivered.
 */
public class PulsarUsgThresholdConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(PulsarUsgThresholdConsumer.class);
    /** Few alerts, but each sends emails: a backlog shouldn't take the database's connections. */
    private static final int CONCURRENCY = 2;

    private final ReactivePulsarClient pulsar;
    private final ObjectMapper objectMapper;
    private final OnUsgThresholdEvtHandler handler;

    public PulsarUsgThresholdConsumer(ReactivePulsarClient pulsar, ObjectMapper objectMapper,
                                      OnUsgThresholdEvtHandler handler) {
        this.pulsar = pulsar;
        this.objectMapper = objectMapper;
        this.handler = handler;
    }

    public void start() {
        pulsar.messageConsumer(Schema.STRING)
                .topic(PulsarTopics.evt("bil", "usg-threshold"))
                .subscriptionName(PulsarTopics.subscription("dfreg", "usage-alerts"))
                .subscriptionInitialPosition(SubscriptionInitialPosition.Earliest)
                .build()
                .consumeMany(messages -> messages.flatMap(message -> Mono
                        .fromCallable(() -> objectMapper.readValue(message.getValue(), Payload.class))
                        .flatMap(p -> handler.handle(new OnUsgThresholdEvtHandler.Threshold(p.dfId(), p.period(),
                                p.threshold(), p.used(), p.quota(), p.planName(), p.resetsAt())))
                        .thenReturn(MessageResult.acknowledge(message.getMessageId(), message.getValue()))
                        .onErrorResume(com.fasterxml.jackson.core.JsonProcessingException.class, e -> {
                            LOG.warn("Dropped an unreadable usage alert: {}", e.getOriginalMessage());
                            return Mono.just(MessageResult.acknowledge(message.getMessageId(), message.getValue()));
                        })
                        .onErrorResume(e -> {
                            LOG.warn("Failed to send a usage alert, negatively acknowledging", e);
                            return Mono.just(MessageResult.negativeAcknowledge(message.getMessageId(),
                                    message.getValue()));
                        }), CONCURRENCY))
                .subscribe();
    }

    record Payload(String dfId, String period, int threshold, long used, long quota, String planName,
                   Instant resetsAt) {
    }
}
