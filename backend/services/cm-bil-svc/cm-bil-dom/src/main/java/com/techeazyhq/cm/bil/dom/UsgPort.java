package com.techeazyhq.cm.bil.dom;

import reactor.core.publisher.Mono;

/**
 * Port over the {@code usg:{dfId}:{period}} Redis counter (plan §11) — the same counter both
 * {@code cm-gw}'s rate limiter and this service's usage view/eligibility check read.
 */
public interface UsgPort {

    Mono<Long> currentPeriodUsage(String dfId);
}
