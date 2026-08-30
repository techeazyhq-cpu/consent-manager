package com.techeazyhq.cm.cnscmd.infra;

import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.web.reactive.function.client.WebClient;

import com.techeazyhq.cm.cnscmd.dom.DfActivePort;

import reactor.core.publisher.Mono;

/** {@link DfActivePort} adapter (plan §7c step 1): Circuit-Breaker-wrapped call to cm-df-reg-svc. */
public class WebClientDfActiveAdapter implements DfActivePort {

    private final WebClient webClient;
    private final ReactiveCircuitBreaker circuitBreaker;

    public WebClientDfActiveAdapter(WebClient.Builder builder, ReactiveCircuitBreakerFactory cbFactory, String dfRegBaseUrl) {
        this.webClient = builder.baseUrl(dfRegBaseUrl).build();
        this.circuitBreaker = cbFactory.create("df-active");
    }

    @Override
    public Mono<Boolean> isActive(String dfId) {
        Mono<Boolean> call = webClient.get()
                .uri("/internal/dfs/{id}/active", dfId)
                .retrieve()
                .bodyToMono(DfActiveResp.class)
                .map(DfActiveResp::active);
        // Fail closed: if cm-df-reg-svc is unreachable, treat the DF as not-active rather
        // than silently allowing a consent request from an unverifiable DF.
        return circuitBreaker.run(call, throwable -> Mono.just(false));
    }

    private record DfActiveResp(boolean active) {
    }
}
