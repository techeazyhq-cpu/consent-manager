package com.techeazyhq.cm.ntf.dom;

import reactor.core.publisher.Mono;

/** Port (plan §9a): implemented by {@code SmtpEmailSenderAdapter} (Mailpit, local) or the notification-service adapter. */
public interface EmailSenderPort {

    Mono<Void> send(NtfMsg msg);
}
