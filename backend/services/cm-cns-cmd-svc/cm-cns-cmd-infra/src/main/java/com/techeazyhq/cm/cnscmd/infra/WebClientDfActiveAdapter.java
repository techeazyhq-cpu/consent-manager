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
