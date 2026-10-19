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

package com.techeazyhq.cm.stf.infra;

import java.time.Duration;

import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.reactive.client.adapter.AdaptedReactivePulsarClientFactory;
import org.apache.pulsar.reactive.client.api.MessageSpec;
import org.apache.pulsar.reactive.client.api.ReactiveMessageSender;
import org.apache.pulsar.reactive.client.api.ReactivePulsarClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeazyhq.cm.audkit.PlatformActionEvt;
import com.techeazyhq.cm.evtkit.PulsarTopics;

import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

/**
 * Staff account changes for the audit ledger (ADR-040), published straight to
 * {@code persistent://cm/stf/evt-platform-action-v1}. cm-stf-svc keeps no database, as Keycloak holds the accounts, so
 * there's no transaction to share with an outbox: the change has already happened in Keycloak by the time it's
 * recorded. Publishing is retried; if it still fails, the event is logged in full at ERROR, to be replayed, and the
 * request isn't failed, since the change it reports stands either way.
 */
public class PulsarStfAuditTrail {

    private static final Logger LOG = LoggerFactory.getLogger(PulsarStfAuditTrail.class);

    private final ObjectMapper objectMapper;
    private final ReactiveMessageSender<String> sender;

    public PulsarStfAuditTrail(ReactivePulsarClient pulsar, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.sender = pulsar.messageSender(Schema.STRING)
                .topic(PulsarTopics.evt("stf", "platform-action"))
                .cache(AdaptedReactivePulsarClientFactory.createCache())
                .maxInflight(100)
                .build();
    }

    public Mono<Void> publish(PlatformActionEvt evt) {
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(evt))
                .flatMap(json -> sender.sendOne(MessageSpec.of(json))
                        .retryWhen(Retry.backoff(3, Duration.ofMillis(200)))
                        .then()
                        .onErrorResume(err -> {
                            LOG.error("Audit event not published, replay it: {}", json, err);
                            return Mono.empty();
                        }));
    }
}
