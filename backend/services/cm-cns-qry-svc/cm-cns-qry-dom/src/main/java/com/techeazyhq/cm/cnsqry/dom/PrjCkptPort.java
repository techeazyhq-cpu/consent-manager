package com.techeazyhq.cm.cnsqry.dom;

import reactor.core.publisher.Mono;

/** Idempotency checkpoint (plan §8): Pulsar is at-least-once, so the Projector dedupes by {@code evtId} before applying. */
public interface PrjCkptPort {

    Mono<Boolean> alreadyProcessed(String evtId);

    Mono<Void> markProcessed(String evtId);
}
