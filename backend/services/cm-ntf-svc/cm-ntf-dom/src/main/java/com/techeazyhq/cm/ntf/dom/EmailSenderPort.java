package com.techeazyhq.cm.ntf.dom;

import reactor.core.publisher.Mono;

/** Port (plan §9a): implemented by {@code SmtpEmailSenderAdapter} — real delivery via Mailpit in Phase 1. */
public interface EmailSenderPort {

    Mono<Void> send(NtfMsg msg);
}
