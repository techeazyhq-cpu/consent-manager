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
package com.techeazyhq.cm.gw;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigurationProperties;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.client.WebClient;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** The PEP leaves an answered request alone (#218): no ERROR for writing headers that have already gone out. */
class PepVerifyGatewayFilterTest {

    private final PepVerifyGatewayFilter pep = new PepVerifyGatewayFilter(WebClient.builder(),
            new ReactiveResilience4JCircuitBreakerFactory(CircuitBreakerRegistry.ofDefaults(),
                    TimeLimiterRegistry.ofDefaults(), null /* no bulkhead */, new Resilience4JConfigurationProperties()),
            "http://127.0.0.1:1");

    private final GatewayFilterChain neverCalled = exchange -> Mono.error(new AssertionError("must not route"));

    @Test
    void aVerificationWithoutAConsentTokenIsDenied() {
        MockServerWebExchange exchange = verify();
        StepVerifier.create(pep.filter(exchange, neverCalled)).verifyComplete();
        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
    }

    @Test
    void aResponseAlreadySentIsLeftAsItWas() {
        MockServerWebExchange exchange = verify();
        exchange.getResponse().setStatusCode(HttpStatus.OK);
        exchange.getResponse().setComplete().block();

        StepVerifier.create(pep.filter(exchange, neverCalled)).verifyComplete();
        assertEquals(HttpStatus.OK, exchange.getResponse().getStatusCode());
    }

    private static MockServerWebExchange verify() {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/api/cns-qry/cns-arts/art-1/verify?purpose=P"));
    }
}
