package com.techeazyhq.cm.dfreg.dom;

import reactor.core.publisher.Mono;

/**
 * Sends an email to an address this service holds, through cm-ntf-svc (ADR-060). {@code ref} identifies the message, so
 * one sent twice, because an event was delivered twice, is the same message to the mail service.
 */
public interface EmailPort {

    Mono<Void> send(String destination, String subject, String body, String ref);
}
