/*
 * Copyright 2026 Vasantha Kumar
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * @author Vasantha Kumar <vasantha.kumar@hotmail.com>
 */

package com.techeazyhq.cm.cnscmd.infra;

import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.techeazyhq.cm.cnscmd.dom.DfUsagePort;
import com.techeazyhq.cm.reskit.DependencyFallbacks;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Mono;

/**
 * {@link DfUsagePort} on cm-bil-svc (ADR-036), behind its circuit breaker (ADR-016): with billing unreachable, a batch
 * is refused with 503 rather than sent unaccounted. A subscription that isn't active is a 402, as for one request.
 */
public class WebClientDfUsageAdapter implements DfUsagePort {

    private final WebClient webClient;
    private final ReactiveCircuitBreaker circuitBreaker;

    public WebClientDfUsageAdapter(WebClient.Builder builder, ReactiveCircuitBreakerFactory<?, ?> cbFactory, String bilBaseUrl) {
        this.webClient = builder.baseUrl(bilBaseUrl).build();
        this.circuitBreaker = cbFactory.create("cm-bil-svc");
    }

    @Override
    public Mono<Reservation> reserve(String dfId, long count) {
        Mono<Reservation> call = webClient.post()
                .uri("/internal/dfs/{id}/usage/reservations", dfId)
                .bodyValue(new CountReq(count))
                .retrieve()
                .bodyToMono(Reservation.class)
                .onErrorMap(e -> e instanceof WebClientResponseException r
                        && (r.getStatusCode().value() == 402 || r.getStatusCode().value() == 404), e -> inactive());
        return circuitBreaker.run(call, DependencyFallbacks.unavailable("cm-bil-svc"));
    }

    @Override
    public Mono<Void> release(String dfId, long count) {
        Mono<Void> call = webClient.post()
                .uri("/internal/dfs/{id}/usage/releases", dfId)
                .bodyValue(new CountReq(count))
                .retrieve()
                .toBodilessEntity()
                .then();
        return circuitBreaker.run(call, DependencyFallbacks.unavailable("cm-bil-svc"));
    }

    @Override
    public Mono<Long> available(String dfId) {
        Mono<Long> call = webClient.get()
                .uri("/internal/dfs/{id}/sub-eligibility", dfId)
                .retrieve()
                .bodyToMono(EligibilityResp.class)
                .map(resp -> "ACTIVE".equals(resp.dfSubSts()) ? Math.max(0, resp.reqPerMonthQta() - resp.curPeriodUsg()) : 0L)
                .onErrorResume(WebClientResponseException.NotFound.class, e -> Mono.just(0L));
        return circuitBreaker.run(call, DependencyFallbacks.unavailable("cm-bil-svc"));
    }

    private static DomRuleViolation inactive() {
        return new DomRuleViolation(402, "subscription-inactive", "The subscription is not active, so nothing can be sent");
    }

    private record CountReq(long count) {
    }

    private record EligibilityResp(String dfSubSts, long curPeriodUsg, long reqPerMonthQta) {
    }
}
