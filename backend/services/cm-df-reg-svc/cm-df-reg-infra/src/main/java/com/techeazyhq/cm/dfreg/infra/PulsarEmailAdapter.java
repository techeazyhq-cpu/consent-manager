package com.techeazyhq.cm.dfreg.infra;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeazyhq.cm.dfreg.dom.EmailPort;
import com.techeazyhq.cm.evtkit.PulsarTopics;

import reactor.core.publisher.Mono;

/** {@link EmailPort}: a command to cm-ntf-svc's {@code cmd-notify-email} topic (ADR-060), which sends the mail. */
public class PulsarEmailAdapter implements EmailPort {

    private final PulsarDfEvtProducer producer;
    private final ObjectMapper objectMapper;

    public PulsarEmailAdapter(PulsarDfEvtProducer producer, ObjectMapper objectMapper) {
        this.producer = producer;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> send(String destination, String subject, String body, String ref) {
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(new NotifyEmailCmdPayload(destination, subject,
                        body, ref)))
                .flatMap(json -> producer.publish(PulsarTopics.cmd("ntf", "notify-email"), json));
    }

    private record NotifyEmailCmdPayload(String destination, String subject, String body, String ref) {
    }
}
