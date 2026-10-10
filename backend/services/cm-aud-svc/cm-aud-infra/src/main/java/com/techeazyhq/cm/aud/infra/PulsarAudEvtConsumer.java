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

package com.techeazyhq.cm.aud.infra;

import com.techeazyhq.cm.aud.app.AudLedgerService;
import com.techeazyhq.cm.evtkit.PulsarTopics;
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.client.api.SubscriptionInitialPosition;
import org.apache.pulsar.reactive.client.api.MessageResult;
import org.apache.pulsar.reactive.client.api.ReactivePulsarClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Records every platform event in the audit ledger (ADR-014): the consent lifecycle and fiduciary activation, from
 * the same topics the rest of the platform acts on, under the ledger's own subscriptions. Messages are handled one at a
 * time per topic and acknowledged only once recorded; a failure is negatively acknowledged so Pulsar redelivers it,
 * and a redelivered event is recognised by its {@code evtId} and recorded once.
 */
public class PulsarAudEvtConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(PulsarAudEvtConsumer.class);

    /** Topic (context, event name) to the event type the ledger records it as. */
    static final Map<String, String[]> TOPICS = Map.of(
            "CnsReqCreatedEvt", new String[] {"cnscmd", "cns-req-created"},
            "CnsGrantedEvt", new String[] {"cnscmd", "cns-granted"},
            "CnsDeniedEvt", new String[] {"cnscmd", "cns-denied"},
            "CnsRevokedEvt", new String[] {"cnscmd", "cns-revoked"},
            "CnsDpLinkedEvt", new String[] {"cnscmd", "cns-dp-linked"},
            "CnsExpiredEvt", new String[] {"cnscmd", "cns-expired"},
            "DfActivatedEvt", new String[] {"dfreg", "df-activated"});

    private final ReactivePulsarClient pulsar;
    private final AudEvtParser parser;
    private final AudLedgerService ledger;

    public PulsarAudEvtConsumer(ReactivePulsarClient pulsar, AudEvtParser parser, AudLedgerService ledger) {
        this.pulsar = pulsar;
        this.parser = parser;
        this.ledger = ledger;
    }

    public void start() {
        TOPICS.forEach((evtType, topic) -> consume(evtType, topic[0], topic[1]));
    }

    private void consume(String evtType, String context, String eventName) {
        String topic = PulsarTopics.evt(context, eventName);
        pulsar.messageConsumer(Schema.STRING)
                .topic(topic)
                .subscriptionName(PulsarTopics.subscription("aud", "ledger-" + eventName))
                .subscriptionInitialPosition(SubscriptionInitialPosition.Earliest)
                .build()
                .consumeMany(messages -> messages.concatMap(message -> Mono.defer(() -> {
                            try {
                                return ledger.record(parser.parse(evtType, message.getValue()));
                            } catch (Exception e) {
                                return Mono.error(e);
                            }
                        })
                        .thenReturn(MessageResult.acknowledge(message.getMessageId(), message.getValue()))
                        .onErrorResume(err -> {
                            LOG.error("Could not record a {} in the audit ledger, negatively acknowledging", evtType, err);
                            return Mono.just(MessageResult.negativeAcknowledge(message.getMessageId(), message.getValue()));
                        })))
                .subscribe();
    }
}
