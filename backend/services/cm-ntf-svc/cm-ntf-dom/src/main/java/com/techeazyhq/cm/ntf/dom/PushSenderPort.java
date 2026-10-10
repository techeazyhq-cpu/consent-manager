package com.techeazyhq.cm.ntf.dom;

import reactor.core.publisher.Mono;

/** Port (plan §9a): app push to a device token ({@link NtfMsg#destination()}). Same shape as {@link EmailSenderPort}. */
public interface PushSenderPort {

    Mono<Void> send(NtfMsg msg);
}
