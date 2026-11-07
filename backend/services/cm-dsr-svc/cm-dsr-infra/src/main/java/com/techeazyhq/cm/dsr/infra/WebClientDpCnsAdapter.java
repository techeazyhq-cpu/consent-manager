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

package com.techeazyhq.cm.dsr.infra;

import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;

import com.techeazyhq.cm.dsr.dom.DpCnsPort;
import com.techeazyhq.cm.reskit.DependencyFallbacks;

import reactor.core.publisher.Mono;

/**
 * {@link DpCnsPort} through cm-cns-qry-svc's own {@code GET /api/cns-qry/cns-arts/{id}}, with the principal's token:
 * the query service answers only for the principal's own consents, so no internal route is opened for this
 * (V-11). A 404 is "not theirs". Behind its circuit breaker (ADR-016).
 */
public class WebClientDpCnsAdapter implements DpCnsPort {

    private final WebClient webClient;
    private final ReactiveCircuitBreaker circuitBreaker;

    public WebClientDpCnsAdapter(WebClient.Builder builder, ReactiveCircuitBreakerFactory<?, ?> cbFactory,
                                 String cnsQryBaseUrl) {
        this.webClient = builder.baseUrl(cnsQryBaseUrl).build();
        this.circuitBreaker = cbFactory.create("cm-cns-qry-svc");
    }

    @Override
    public Mono<DpCns> find(String cnsReqId, String principalToken) {
        Mono<DpCns> call = webClient.get()
                .uri("/api/cns-qry/cns-arts/{id}", cnsReqId)
                .headers(h -> h.setBearerAuth(principalToken))
                .exchangeToMono(resp -> {
                    if (resp.statusCode() == HttpStatus.NOT_FOUND) {
                        return resp.releaseBody().then(Mono.empty());
                    }
                    return resp.statusCode().is2xxSuccessful() ? resp.bodyToMono(CnsArtResp.class)
                            : resp.createError();
                })
                .map(row -> new DpCns(row.cnsReqId(), row.dfId(), row.prpsCode()));
        return circuitBreaker.run(call, DependencyFallbacks.unavailable("cm-cns-qry-svc"));
    }

    private record CnsArtResp(String cnsReqId, String dfId, String prpsCode) {
    }
}
