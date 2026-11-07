package com.techeazyhq.cm.bil.dom;

import reactor.core.publisher.Mono;

/** Where a raised alert goes next: written in the alert's own transaction, relayed to Pulsar after it commits. */
public interface UsgAlertEvtPort {

    Mono<Void> thresholdReached(UsgThresholdEvt evt);
}
