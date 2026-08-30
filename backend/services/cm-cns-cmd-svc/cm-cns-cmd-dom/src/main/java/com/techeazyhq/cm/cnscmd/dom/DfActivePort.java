package com.techeazyhq.cm.cnscmd.dom;

import reactor.core.publisher.Mono;

/** Port (plan §7c step 1): Circuit-Breaker-wrapped check that a DF is ACTIVE, implemented against cm-df-reg-svc in -infra. */
public interface DfActivePort {

    Mono<Boolean> isActive(String dfId);
}
