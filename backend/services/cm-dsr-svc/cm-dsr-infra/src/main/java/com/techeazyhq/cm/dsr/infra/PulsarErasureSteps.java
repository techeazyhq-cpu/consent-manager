package com.techeazyhq.cm.dsr.infra;

import java.util.List;
import java.util.Map;

import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.client.api.SubscriptionInitialPosition;
import org.apache.pulsar.reactive.client.api.MessageResult;
import org.apache.pulsar.reactive.client.api.MessageSpec;
import org.apache.pulsar.reactive.client.api.ReactiveMessageSender;
import org.apache.pulsar.reactive.client.api.ReactivePulsarClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeazyhq.cm.dsr.app.DpErasureHandler;
import com.techeazyhq.cm.dsr.dom.DpErasure;
import com.techeazyhq.cm.dsr.dom.DpErasurePorts;
import com.techeazyhq.cm.evtkit.ErasureSteps;
import com.techeazyhq.cm.evtkit.PulsarTopics;

import reactor.core.publisher.Mono;

/**
 * The erasure saga's wire, the orchestrator's end (ADR-059): sends a remote step on {@code dsr/cmd-erasure-step}, and
 * hands each answer on {@code dsr/evt-erasure-step-done} to {@link DpErasureHandler}. Sent directly, not through the
 * outbox: a command lost is sent again by the handler's redrive, and every step is idempotent.
 */
public class PulsarErasureSteps implements DpErasurePorts.Steps {

    private static final Logger LOG = LoggerFactory.getLogger(PulsarErasureSteps.class);

    private final ReactivePulsarClient pulsar;
    private final ObjectMapper json;
    private final ReactiveMessageSender<String> sender;

    public PulsarErasureSteps(ReactivePulsarClient pulsar, ObjectMapper json) {
        this.pulsar = pulsar;
        this.json = json;
        this.sender = pulsar.messageSender(Schema.STRING)
                .topic(PulsarTopics.cmd(ErasureSteps.CTX, ErasureSteps.CMD))
                .build();
    }

    /**
     * Each step gets only what it needs: the email goes to the notice, and to the consents step, which claims the
     * requests sent to it that the principal never signed in to see.
     */
    @Override
    public Mono<Void> send(DpErasure erasure) {
        boolean needsEmail = erasure.step() == DpErasure.Step.NOTICE_AND_DEVICES
                || erasure.step() == DpErasure.Step.CONSENTS;
        ErasureSteps.Cmd cmd = new ErasureSteps.Cmd(erasure.id(), erasure.step().name(), erasure.dpId(),
                needsEmail ? erasure.email() : null,
                erasure.step() == DpErasure.Step.AUDIT ? erasure.cnsReqIds() : List.of());
        return Mono.fromCallable(() -> json.writeValueAsString(cmd))
                .flatMap(body -> sender.sendOne(MessageSpec.of(body)))
                .then();
    }

    /** Hands every answer to {@code handler}; a failure is redelivered. */
    public void listen(DpErasureHandler handler) {
        pulsar.messageConsumer(Schema.STRING)
                .topic(PulsarTopics.evt(ErasureSteps.CTX, ErasureSteps.DONE))
                .subscriptionName(PulsarTopics.subscription("dsr", "erasure-saga"))
                .subscriptionInitialPosition(SubscriptionInitialPosition.Earliest)
                .build()
                .consumeMany(messages -> messages.concatMap(message -> Mono
                        .fromCallable(() -> json.readValue(message.getValue(), ErasureSteps.Done.class))
                        .flatMap(done -> handler.onStepDone(done.erasureId(), done.step(), ids(done.result()),
                                count(done.result())))
                        .thenReturn(MessageResult.acknowledge(message.getMessageId(), message.getValue()))
                        .onErrorResume(e -> {
                            LOG.warn("Failed to handle an erasure step answer, negatively acknowledging", e);
                            return Mono.just(MessageResult.negativeAcknowledge(message.getMessageId(),
                                    message.getValue()));
                        })))
                .subscribe();
    }

    @SuppressWarnings("unchecked")
    private static List<String> ids(Map<String, Object> result) {
        Object ids = result == null ? null : result.get("cnsReqIds");
        return ids instanceof List<?> list ? (List<String>) list : List.of();
    }

    private static Integer count(Map<String, Object> result) {
        Object n = result == null ? null : result.get("withdrawn");
        return n instanceof Number number ? number.intValue() : 0;
    }
}
