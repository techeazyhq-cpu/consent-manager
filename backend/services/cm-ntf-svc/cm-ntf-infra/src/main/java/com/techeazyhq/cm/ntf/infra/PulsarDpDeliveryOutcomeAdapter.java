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

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.reactive.client.adapter.AdaptedReactivePulsarClientFactory;
import org.apache.pulsar.reactive.client.api.MessageSpec;
import org.apache.pulsar.reactive.client.api.ReactiveMessageSender;
import org.apache.pulsar.reactive.client.api.ReactivePulsarClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeazyhq.cm.evtkit.PulsarTopics;
import com.techeazyhq.cm.ntf.dom.DpDeliveryOutcomePort;
import com.techeazyhq.cm.ntf.dom.NtfChnl;

import reactor.core.publisher.Mono;

/**
 * {@link DpDeliveryOutcomePort} as {@code NtfDpDeliveryFailedEvt} on {@code persistent://cm/ntf/evt-dp-delivery-failed-v1},
 * consumed by cm-cns-cmd-svc's saga. Published directly rather than through an outbox: cm-ntf-svc changes no state
 * of its own here, and the notify command is only acknowledged after the send, so a failed send redelivers the
 * command (notification-service's idempotency key keeps that from emailing twice) and the event is sent again.
 */
public class PulsarDpDeliveryOutcomeAdapter implements DpDeliveryOutcomePort {

    static final String TOPIC = PulsarTopics.evt("ntf", "dp-delivery-failed");

    private final ObjectMapper objectMapper;
    private final ReactiveMessageSender<String> sender;

    public PulsarDpDeliveryOutcomeAdapter(ReactivePulsarClient reactivePulsarClient, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.sender = reactivePulsarClient.messageSender(Schema.STRING)
                .topic(TOPIC)
                .cache(AdaptedReactivePulsarClientFactory.createCache())
                .maxInflight(100)
                .build();
    }

    @Override
    public Mono<Void> deliveryFailed(String cnsReqId, NtfChnl channel, String code) {
        return Mono.fromCallable(() -> {
                    Map<String, Object> evt = new LinkedHashMap<>();
                    evt.put("evtId", UUID.randomUUID().toString());
                    evt.put("occurredAt", Instant.now().toString());
                    evt.put("cnsReqId", cnsReqId);
                    evt.put("channel", channel.name());
                    evt.put("code", code);
                    return objectMapper.writeValueAsString(evt);
                })
                .flatMap(json -> sender.sendOne(MessageSpec.of(json)))
                .then();
    }
}
