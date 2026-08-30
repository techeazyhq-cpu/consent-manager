package com.techeazyhq.cm.cnscmd.dom;

import com.techeazyhq.cm.domkit.Spec;

import reactor.core.publisher.Mono;

/**
 * Specification (plan §7b, §12): a DF may create a new {@link CnsReq} only if its
 * subscription is {@code ACTIVE} and under its monthly quota. Unlike {@link CnsReqDecidablePolicy}
 * this needs a call to {@code cm-bil-svc} (Circuit-Breaker-wrapped), so it's a port —
 * implemented by an infra adapter, not a pure in-memory check.
 */
public interface DfSubEligiblePolicy {

    /**
     * @return a violated {@link Spec.SpecResult} with reason code {@code subscription-inactive}
     *         or {@code quota-exceeded} (mapped to 402/429 respectively by the command handler,
     *         plan §7b), or {@link Spec.SpecResult#ok()}.
     */
    Mono<Spec.SpecResult> evaluate(String dfId);
}
