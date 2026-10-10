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
import org.apache.pulsar.client.api.SubscriptionInitialPosition;
import org.apache.pulsar.reactive.client.api.MessageResult;
import org.apache.pulsar.reactive.client.api.ReactivePulsarClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeazyhq.cm.cnscmd.app.CnsLifecycleSagaOrchestrator;
import com.techeazyhq.cm.cnscmd.dom.CnsReqSts;
import com.techeazyhq.cm.evtkit.PulsarTopics;

import reactor.core.publisher.Mono;

/**
 * Subscribes cm-cns-cmd-svc to its own emitted event topics to actually drive
 * {@link CnsLifecycleSagaOrchestrator} — without this, the orchestrator existed as a bean but
 * was never invoked by anything, so no DP/DF notification ever fired for any consent lifecycle
 * transition (found only once an actual UI action was checked against Mailpit — no error, no
 * exception, just silence, since the write path never depended on this at all). Mirrors
 * cm-cns-qry-svc's {@code PulsarCnsEvtConsumer} pattern. {@code start()} is called once from
 * the boot module.
 */
public class PulsarCnsSagaEvtConsumer {

    private static final String CTX = "cnscmd";

    private final ReactivePulsarClient reactivePulsarClient;
    private final ObjectMapper objectMapper;
    private final CnsLifecycleSagaOrchestrator orchestrator;

    public PulsarCnsSagaEvtConsumer(ReactivePulsarClient reactivePulsarClient, ObjectMapper objectMapper,
                                     CnsLifecycleSagaOrchestrator orchestrator) {
        this.reactivePulsarClient = reactivePulsarClient;
        this.objectMapper = objectMapper;
        this.orchestrator = orchestrator;
    }

    public void start() {
        consume("cns-req-created", "saga-notify-dp", CnsReqCreatedEvtPayload.class,
                payload -> orchestrator.onCnsReqCreated(payload.cnsReqId()));
        consume("cns-granted", "saga-notify-df", CnsGrantedEvtPayload.class,
                payload -> orchestrator.onCnsDecided(payload.dfId(), payload.cnsReqId(), CnsReqSts.GRANTED));
        consume("cns-denied", "saga-notify-df", CnsDeniedEvtPayload.class,
                payload -> orchestrator.onCnsDecided(payload.dfId(), payload.cnsReqId(), CnsReqSts.DENIED));
        consume("cns-revoked", "saga-notify-df", CnsRevokedEvtPayload.class,
                payload -> orchestrator.onCnsRevoked(payload.dfId(), payload.cnsArtId()));
    }

    private <T> void consume(String eventNameKebab, String subscriptionSuffix, Class<T> payloadType,
                              java.util.function.Function<T, Mono<Void>> handler) {
        String topic = PulsarTopics.evt(CTX, eventNameKebab);
        String subscription = PulsarTopics.subscription(CTX, subscriptionSuffix + "-" + eventNameKebab);
        reactivePulsarClient.messageConsumer(Schema.STRING)
                .topic(topic)
                .subscriptionName(subscription)
                .subscriptionInitialPosition(SubscriptionInitialPosition.Earliest)
                .build()
                .consumeMany(messageFlux -> messageFlux.flatMap(message -> {
                    try {
                        T payload = objectMapper.readValue(message.getValue(), payloadType);
                        return handler.apply(payload)
                                .thenReturn(MessageResult.acknowledge(message.getMessageId(), message.getValue()));
                    } catch (Exception e) {
                        org.slf4j.LoggerFactory.getLogger(PulsarCnsSagaEvtConsumer.class)
                                .warn("Failed to process saga message on {}, negatively acknowledging", topic, e);
                        return Mono.just(MessageResult.negativeAcknowledge(message.getMessageId(), message.getValue()));
                    }
                }))
                .subscribe();
    }

    record CnsReqCreatedEvtPayload(String cnsReqId) {
    }

    record CnsGrantedEvtPayload(String cnsReqId, String dfId) {
    }

    record CnsDeniedEvtPayload(String cnsReqId, String dfId) {
    }

    record CnsRevokedEvtPayload(String cnsArtId, String dfId) {
    }
}
