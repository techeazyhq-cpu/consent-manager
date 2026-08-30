package com.techeazyhq.cm.ntf.dom;

import reactor.core.publisher.Mono;

/** Port (plan §9a): implemented by {@code WhatsAppCatcherAdapter} — same self-built-catcher reasoning as {@link SmsSenderPort}. */
public interface WhatsAppSenderPort {

    Mono<Void> send(NtfMsg msg);
}
