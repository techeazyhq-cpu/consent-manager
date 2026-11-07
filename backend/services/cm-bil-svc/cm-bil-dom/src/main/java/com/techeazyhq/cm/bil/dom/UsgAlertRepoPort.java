package com.techeazyhq.cm.bil.dom;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface UsgAlertRepoPort {

    /**
     * Records the alert unless this fiduciary already has one for the month and threshold: true when it was new, so
     * that of several replicas sweeping at once exactly one raises it.
     */
    Mono<Boolean> addIfNew(UsgAlert alert);

    /** A fiduciary's alerts, newest first. */
    Flux<UsgAlert> findByDf(String dfId, int limit);

    /** Every fiduciary's alerts for {@code period} ({@code yyyy-MM}), newest first. */
    Flux<UsgAlert> findByPeriod(String period);
}
