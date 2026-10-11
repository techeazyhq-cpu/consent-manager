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

package com.techeazyhq.cm.dfreg.infra;

import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.techeazyhq.cm.dfreg.dom.SubPlnCatalogPort;
import com.techeazyhq.cm.reskit.DependencyFallbacks;

import reactor.core.publisher.Mono;

/**
 * {@link SubPlnCatalogPort} against cm-bil-svc's {@code GET /internal/sub-plns/{name}}, behind a circuit breaker
 * (ADR-016). A 404 is an unknown plan; with cm-bil-svc unreachable, onboarding fails with 503 rather than accept a
 * plan nobody could check.
 */
public class WebClientSubPlnCatalogAdapter implements SubPlnCatalogPort {

    private final WebClient webClient;
    private final ReactiveCircuitBreaker circuitBreaker;

    public WebClientSubPlnCatalogAdapter(WebClient.Builder builder, ReactiveCircuitBreakerFactory<?, ?> cbFactory,
                                         String bilBaseUrl) {
        this.webClient = builder.baseUrl(bilBaseUrl).build();
        this.circuitBreaker = cbFactory.create("cm-bil-svc");
    }

    @Override
    public Mono<Boolean> exists(String subPlnName) {
        Mono<Boolean> call = webClient.get()
                .uri("/internal/sub-plns/{name}", subPlnName)
                .retrieve()
                .toBodilessEntity()
                .thenReturn(true)
                .onErrorResume(WebClientResponseException.NotFound.class, notFound -> Mono.just(false));
        return circuitBreaker.run(call, DependencyFallbacks.unavailable("cm-bil-svc"));
    }
}
