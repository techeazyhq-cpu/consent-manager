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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigurationProperties;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.client.WebClient;

import com.sun.net.httpserver.HttpServer;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * The PEP's answers (ADR-006, ADR-057): forwards an allow, 403 for a deny, and 503 with Retry-After when the PDP
 * couldn't decide (V-17), never an allow. Leaves an answered request alone (#218).
 */
class PepVerifyGatewayFilterTest {

    private static final Duration OPEN_WAIT = Duration.ofSeconds(30);

    private final CircuitBreakerRegistry breakers = CircuitBreakerRegistry.ofDefaults();
    private final AtomicInteger forwarded = new AtomicInteger();
    private final GatewayFilterChain chain = exchange -> Mono.fromRunnable(forwarded::incrementAndGet);
    private final GatewayFilterChain neverCalled = exchange -> Mono.error(new AssertionError("must not route"));
    private HttpServer opa;

    @AfterEach
    void stopOpa() {
        if (opa != null) {
            opa.stop(0);
        }
    }

    @Test
    void aVerificationWithoutAConsentTokenIsDenied() {
        MockServerWebExchange exchange = verify(null);
        StepVerifier.create(pep("http://127.0.0.1:1").filter(exchange, neverCalled)).verifyComplete();
        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
    }

    @Test
    void aResponseAlreadySentIsLeftAsItWas() {
        MockServerWebExchange exchange = verify("token");
        exchange.getResponse().setStatusCode(HttpStatus.OK);
        exchange.getResponse().setComplete().block();

        StepVerifier.create(pep("http://127.0.0.1:1").filter(exchange, neverCalled)).verifyComplete();
        assertEquals(HttpStatus.OK, exchange.getResponse().getStatusCode());
    }

    @Test
    void anAllowIsForwarded() {
        MockServerWebExchange exchange = run(opaAnswering("{\"result\":{\"allow\":true,\"undetermined\":false}}"));

        assertEquals(1, forwarded.get());
        assertNull(exchange.getResponse().getStatusCode());
    }

    @Test
    void aDenyFromThePdpIs403() {
        MockServerWebExchange exchange = run(opaAnswering("{\"result\":{\"allow\":false,\"undetermined\":false}}"));

        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
        assertTrue(body(exchange).contains("urn:cm:problem:consent-not-verified"));
        assertNull(exchange.getResponse().getHeaders().getFirst("Retry-After"));
        assertEquals(0, forwarded.get());
    }

    @Test
    void aPdpThatCouldNotCheckIs503() {
        MockServerWebExchange exchange = run(opaAnswering("{\"result\":{\"allow\":false,\"undetermined\":true}}"));

        assertUnavailable(exchange, "1");
    }

    @Test
    void aPdpWithoutADecisionIs503() {
        assertUnavailable(run(opaAnswering("{}")), "1");
    }

    @Test
    void anUnreachablePdpIs503() {
        assertUnavailable(run(pep("http://127.0.0.1:1")), "1");
    }

    @Test
    void aPdpTooSlowToAnswerIs503() {
        opa = server(exchange -> {
            try {
                Thread.sleep(2_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            respond(exchange, "{\"result\":{\"allow\":true,\"undetermined\":false}}");
        });
        PepVerifyGatewayFilter pep = new PepVerifyGatewayFilter(WebClient.builder(), factory(Duration.ofMillis(200)),
                url(opa), OPEN_WAIT);

        assertUnavailable(run(pep), "1");
    }

    @Test
    void anOpenBreakerIs503UntilItMayCloseAndNeverAsksThePdp() {
        AtomicInteger asked = new AtomicInteger();
        opa = server(exchange -> {
            asked.incrementAndGet();
            respond(exchange, "{\"result\":{\"allow\":true,\"undetermined\":false}}");
        });
        PepVerifyGatewayFilter pep = pep(url(opa));
        breakers.circuitBreaker("pdp").transitionToForcedOpenState();

        assertUnavailable(run(pep), "30");
        assertEquals(0, asked.get());
    }

    private void assertUnavailable(MockServerWebExchange exchange, String retryAfter) {
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exchange.getResponse().getStatusCode());
        assertTrue(body(exchange).contains("urn:cm:problem:consent-check-unavailable"), body(exchange));
        assertEquals(retryAfter, exchange.getResponse().getHeaders().getFirst("Retry-After"));
        assertEquals(0, forwarded.get(), "an unknown decision is never forwarded");
    }

    private MockServerWebExchange run(PepVerifyGatewayFilter pep) {
        MockServerWebExchange exchange = verify("token");
        pep.filter(exchange, chain).block(Duration.ofSeconds(10));
        return exchange;
    }

    private PepVerifyGatewayFilter opaAnswering(String json) {
        opa = server(exchange -> respond(exchange, json));
        return pep(url(opa));
    }

    private PepVerifyGatewayFilter pep(String pdpBaseUrl) {
        return new PepVerifyGatewayFilter(WebClient.builder(), factory(Duration.ofSeconds(2)), pdpBaseUrl, OPEN_WAIT);
    }

    private ReactiveResilience4JCircuitBreakerFactory factory(Duration timeLimit) {
        return new ReactiveResilience4JCircuitBreakerFactory(breakers,
                TimeLimiterRegistry.of(TimeLimiterConfig.custom().timeoutDuration(timeLimit).build()),
                null /* no bulkhead */, new Resilience4JConfigurationProperties());
    }

    private static MockServerWebExchange verify(String cnsTkn) {
        MockServerHttpRequest.BaseBuilder<?> request =
                MockServerHttpRequest.get("/api/cns-qry/cns-arts/art-1/verify?purpose=P");
        if (cnsTkn != null) {
            request.header("X-Cns-Tkn", cnsTkn);
        }
        return MockServerWebExchange.from(request);
    }

    private static String body(MockServerWebExchange exchange) {
        return exchange.getResponse().getBodyAsString().block(Duration.ofSeconds(1));
    }

    private static String url(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static HttpServer server(com.sun.net.httpserver.HttpHandler handler) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/v1/data/consent/authorization/decision", handler);
            server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
