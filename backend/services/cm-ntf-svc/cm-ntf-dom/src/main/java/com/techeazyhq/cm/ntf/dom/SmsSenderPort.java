package com.techeazyhq.cm.ntf.dom;

import reactor.core.publisher.Mono;

/**
 * Port (plan §9a): implemented by {@code SmsCatcherAdapter} in Phase 1. No trustworthy free
 * OSS SMS gateway/catcher exists the way Mailpit exists for email, so this platform builds
 * its own minimal capture-and-inspect adapter for local dev/testing rather than depending on
 * an obscure, unmaintained third-party Docker image or a paid gateway (Twilio/MSG91/etc.,
 * Phase 2 backlog). Same shape as {@link EmailSenderPort} so both fan out identically from
 * {@code NotifyDpCmd}/{@code NotifyDfCmd} handlers.
 */
public interface SmsSenderPort {

    Mono<Void> send(NtfMsg msg);
}
