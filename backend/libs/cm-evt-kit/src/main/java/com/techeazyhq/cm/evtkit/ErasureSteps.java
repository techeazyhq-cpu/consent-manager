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

package com.techeazyhq.cm.evtkit;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.client.api.SubscriptionInitialPosition;
import org.apache.pulsar.reactive.client.api.MessageResult;
import org.apache.pulsar.reactive.client.api.MessageSpec;
import org.apache.pulsar.reactive.client.api.ReactiveMessageSender;
import org.apache.pulsar.reactive.client.api.ReactivePulsarClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Mono;

/**
 * The erasure saga's wire (ADR-059). cm-dsr-svc orchestrates: it sends each step as a command on
 * {@code dsr/cmd-erasure-step}, and the service that owns the step does it and answers on
 * {@code dsr/evt-erasure-step-done}. Every service subscribes under its own name and takes only its own step. Steps
 * are idempotent: a command is sent again when no answer came, and a step done twice answers twice, which the
 * orchestrator ignores.
 */
public final class ErasureSteps {

    /** cm-ntf-svc: tell the principal their account is closing, then forget their push devices. */
    public static final String NOTICE_AND_DEVICES = "NOTICE_AND_DEVICES";
    /** cm-cns-cmd-svc: withdraw their consents, decline what waits for them, and erase the contacts it holds. */
    public static final String CONSENTS = "CONSENTS";
    /** cm-aud-svc: erase the ledger's payloads about them and the links to their entries. */
    public static final String AUDIT = "AUDIT";

    public static final String CTX = "dsr";
    public static final String CMD = "erasure-step";
    public static final String DONE = "erasure-step-done";

    private static final Logger LOG = LoggerFactory.getLogger(ErasureSteps.class);

    private ErasureSteps() {
    }

    /**
     * One step for one principal. {@code email} is there for the notice only; {@code cnsReqIds} are the consent requests
     * the consents step found, for the audit step to erase entries about.
     */
    public record Cmd(String erasureId, String step, String dpId, String email, List<String> cnsReqIds) {
    }

    /** A step done: {@code result} holds what the next steps need, and counts for the record. */
    public record Done(String erasureId, String step, Map<String, Object> result) {
    }

    /**
     * Subscribes {@code service} to the step commands and does {@code step} with {@code handler}, answering when it
     * succeeds. A failure is negatively acknowledged, so Pulsar redelivers it; the orchestrator also sends it again
     * when no answer comes.
     */
    public static void serve(ReactivePulsarClient pulsar, ObjectMapper json, String service, String step,
                             Function<Cmd, Mono<Map<String, Object>>> handler) {
        ReactiveMessageSender<String> replies = pulsar.messageSender(Schema.STRING)
                .topic(PulsarTopics.evt(CTX, DONE))
                .build();
        pulsar.messageConsumer(Schema.STRING)
                .topic(PulsarTopics.cmd(CTX, CMD))
                .subscriptionName(PulsarTopics.subscription(service, "erasure-" + step.toLowerCase().replace('_', '-')))
                .subscriptionInitialPosition(SubscriptionInitialPosition.Earliest)
                .build()
                .consumeMany(messages -> messages.concatMap(message -> Mono
                        .fromCallable(() -> json.readValue(message.getValue(), Cmd.class))
                        .flatMap(cmd -> !step.equals(cmd.step()) ? Mono.empty()
                                : handler.apply(cmd)
                                        .defaultIfEmpty(Map.of())
                                        .map(result -> new Done(cmd.erasureId(), step, result))
                                        .flatMap(done -> Mono.fromCallable(() -> json.writeValueAsString(done)))
                                        .flatMap(body -> replies.sendOne(MessageSpec.of(body)))
                                        .doOnSuccess(id -> LOG.info("Erasure {}: {} done", cmd.erasureId(), step)))
                        .thenReturn(MessageResult.acknowledge(message.getMessageId(), message.getValue()))
                        // a command that can't be read never will be: drop it rather than loop
                        .onErrorResume(com.fasterxml.jackson.core.JsonProcessingException.class, e -> {
                            LOG.warn("Dropped an unreadable erasure command: {}", e.getOriginalMessage());
                            return Mono.just(MessageResult.acknowledge(message.getMessageId(), message.getValue()));
                        })
                        .onErrorResume(e -> {
                            LOG.warn("Erasure step {} failed, negatively acknowledging", step, e);
                            return Mono.just(MessageResult.negativeAcknowledge(message.getMessageId(), message.getValue()));
                        })))
                .subscribe();
    }
}
