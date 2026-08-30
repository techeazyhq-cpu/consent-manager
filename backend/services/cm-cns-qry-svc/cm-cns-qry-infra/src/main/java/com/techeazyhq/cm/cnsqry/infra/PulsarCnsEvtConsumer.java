package com.techeazyhq.cm.cnsqry.infra;

import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.client.api.SubscriptionInitialPosition;
import org.apache.pulsar.reactive.client.api.MessageResult;
import org.apache.pulsar.reactive.client.api.ReactivePulsarClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeazyhq.cm.cnsqry.app.Projector;
import com.techeazyhq.cm.cnsqry.app.evt.CnsDeniedEvtPayload;
import com.techeazyhq.cm.cnsqry.app.evt.CnsDpLinkedEvtPayload;
import com.techeazyhq.cm.cnsqry.app.evt.CnsExpiredEvtPayload;
import com.techeazyhq.cm.cnsqry.app.evt.CnsGrantedEvtPayload;
import com.techeazyhq.cm.cnsqry.app.evt.CnsRevokedEvtPayload;
import com.techeazyhq.cm.cnsqry.app.evt.CnsReqCreatedEvtPayload;
import com.techeazyhq.cm.evtkit.PulsarTopics;

import reactor.core.publisher.Mono;

/**
 * Subscribes to each of cm-cns-cmd-svc's 6 event topics individually (plan §8's "Key_Shared
 * consumer, topic pattern" simplified to explicit per-topic subscriptions for Phase 1 — the
 * event set is small and fixed, and the reactive client's exact pattern-subscription builder
 * API wasn't something this implementation could verify with confidence; functionally
 * equivalent for a known topic list). {@code start()} is called once from the boot module.
 */
public class PulsarCnsEvtConsumer {

    private static final String CTX = "cnscmd";

    private final ReactivePulsarClient reactivePulsarClient;
    private final ObjectMapper objectMapper;
    private final Projector projector;

    public PulsarCnsEvtConsumer(ReactivePulsarClient reactivePulsarClient, ObjectMapper objectMapper, Projector projector) {
        this.reactivePulsarClient = reactivePulsarClient;
        this.objectMapper = objectMapper;
        this.projector = projector;
    }

    public void start() {
        consume("cns-req-created", CnsReqCreatedEvtPayload.class, projector::onCnsReqCreated);
        consume("cns-granted", CnsGrantedEvtPayload.class, projector::onCnsGranted);
        consume("cns-denied", CnsDeniedEvtPayload.class, projector::onCnsDenied);
        consume("cns-revoked", CnsRevokedEvtPayload.class, projector::onCnsRevoked);
        consume("cns-expired", CnsExpiredEvtPayload.class, projector::onCnsExpired);
        consume("cns-dp-linked", CnsDpLinkedEvtPayload.class, projector::onCnsDpLinked);
    }

    private <T> void consume(String eventNameKebab, Class<T> payloadType, java.util.function.Function<T, Mono<Void>> handler) {
        String topic = PulsarTopics.evt(CTX, eventNameKebab);
        String subscription = PulsarTopics.subscription(CTX, "projector-" + eventNameKebab);
        reactivePulsarClient.messageConsumer(Schema.STRING)
                .topic(topic)
                .subscriptionName(subscription)
                // Default is Latest — wrong for the projector, an idempotent (evtId-deduped)
                // handler that should rebuild the read model from every retained event on a
                // fresh subscription, not just future ones. See PulsarDfActivatedConsumer's
                // comment for the bug this fixes.
                .subscriptionInitialPosition(SubscriptionInitialPosition.Earliest)
                .build()
                .consumeMany(messageFlux -> messageFlux.flatMap(message -> {
                    try {
                        T payload = objectMapper.readValue(message.getValue(), payloadType);
                        return handler.apply(payload)
                                .thenReturn(MessageResult.acknowledge(message.getMessageId(), message.getValue()));
                    } catch (Exception e) {
                        org.slf4j.LoggerFactory.getLogger(PulsarCnsEvtConsumer.class)
                                .warn("Failed to project message on {}, negatively acknowledging", topic, e);
                        return Mono.just(MessageResult.negativeAcknowledge(message.getMessageId(), message.getValue()));
                    }
                }))
                .subscribe();
    }
}
