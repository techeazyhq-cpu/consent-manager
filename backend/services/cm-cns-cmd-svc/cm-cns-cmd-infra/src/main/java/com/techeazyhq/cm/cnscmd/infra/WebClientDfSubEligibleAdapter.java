package com.techeazyhq.cm.cnscmd.infra;

import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.web.reactive.function.client.WebClient;

import com.techeazyhq.cm.cnscmd.dom.DfSubEligiblePolicy;
import com.techeazyhq.cm.domkit.Spec;

import reactor.core.publisher.Mono;

/** {@link DfSubEligiblePolicy} adapter (plan §7b, §11): Circuit-Breaker-wrapped call to cm-bil-svc's internal eligibility endpoint. */
public class WebClientDfSubEligibleAdapter implements DfSubEligiblePolicy {

    private final WebClient webClient;
    private final ReactiveCircuitBreaker circuitBreaker;

    public WebClientDfSubEligibleAdapter(WebClient.Builder builder, ReactiveCircuitBreakerFactory cbFactory, String bilBaseUrl) {
        this.webClient = builder.baseUrl(bilBaseUrl).build();
        this.circuitBreaker = cbFactory.create("df-sub-eligible");
    }

    @Override
    public Mono<Spec.SpecResult> evaluate(String dfId) {
        Mono<Spec.SpecResult> call = webClient.get()
                .uri("/internal/dfs/{id}/sub-eligibility", dfId)
                .retrieve()
                .bodyToMono(SubEligibilityResp.class)
                .map(this::toSpecResult);
        // Fail closed: cm-bil-svc unreachable -> treat as ineligible (402) rather than let
        // billing/quota enforcement be silently bypassed during an outage.
        return circuitBreaker.run(call, throwable ->
                Mono.just(Spec.SpecResult.violated("subscription-inactive")));
    }

    private Spec.SpecResult toSpecResult(SubEligibilityResp resp) {
        if (!"ACTIVE".equals(resp.dfSubSts())) {
            return Spec.SpecResult.violated("subscription-inactive");
        }
        if (resp.curPeriodUsg() >= resp.reqPerMonthQta()) {
            return Spec.SpecResult.violated("quota-exceeded");
        }
        return Spec.SpecResult.ok();
    }

    private record SubEligibilityResp(String dfSubSts, long curPeriodUsg, long reqPerMonthQta) {
    }
}
