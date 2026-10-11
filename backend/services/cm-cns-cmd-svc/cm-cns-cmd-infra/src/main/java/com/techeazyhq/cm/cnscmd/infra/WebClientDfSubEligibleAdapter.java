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

import com.techeazyhq.cm.cnscmd.dom.DfSubEligiblePolicy;
import com.techeazyhq.cm.domkit.Spec;
import com.techeazyhq.cm.reskit.DependencyFallbacks;

import reactor.core.publisher.Mono;

/**
 * {@link DfSubEligiblePolicy} adapter (plan §7b, §11): Circuit-Breaker-wrapped call to cm-bil-svc's internal eligibility
 * endpoint (ADR-016). A fiduciary without a subscription (404) is ineligible; an unreachable cm-bil-svc fails closed with
 * 503, so quota enforcement is never bypassed during an outage, and the fiduciary is not told its subscription lapsed.
 */
public class WebClientDfSubEligibleAdapter implements DfSubEligiblePolicy {

    private final WebClient webClient;
    private final ReactiveCircuitBreaker circuitBreaker;

    public WebClientDfSubEligibleAdapter(WebClient.Builder builder, ReactiveCircuitBreakerFactory cbFactory, String bilBaseUrl) {
        this.webClient = builder.baseUrl(bilBaseUrl).build();
        this.circuitBreaker = cbFactory.create("cm-bil-svc");
    }

    @Override
    public Mono<Spec.SpecResult> evaluate(String dfId) {
        Mono<Spec.SpecResult> call = webClient.get()
                .uri("/internal/dfs/{id}/sub-eligibility", dfId)
                .retrieve()
                .bodyToMono(SubEligibilityResp.class)
                .map(this::toSpecResult)
                .onErrorResume(WebClientResponseException.NotFound.class,
                        notFound -> Mono.just(Spec.SpecResult.violated("subscription-inactive")));
        return circuitBreaker.run(call, DependencyFallbacks.unavailable("cm-bil-svc"));
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
