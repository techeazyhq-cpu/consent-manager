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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.reactive.function.client.WebClient;

import com.sun.net.httpserver.HttpServer;

import reactor.core.publisher.Mono;

/**
 * The per-second limits (ADR-048): the plan's quota for a fiduciary's systems, a bucket of their own for its people in
 * the DF Console. Redis is an in-memory counter that ignores the second in the key, so a test never straddles two
 * windows; cm-bil-svc is a stub giving the FREE plan's 5 a second.
 */
class DfQuotaRateLimitGlobalFilterTest {

    private static final String CM_DF = "http://localhost:8080/realms/cm-df";
    private static final long FREE_REQ_PER_SEC = 5;
    private static final long MEMBER_REQ_PER_SEC = 20;
    private static final HttpServer BIL = freePlanBil();

    private final Map<String, AtomicLong> counters = new ConcurrentHashMap<>();
    private final AtomicInteger forwarded = new AtomicInteger();
    private final GatewayFilterChain chain = exchange -> Mono.fromRunnable(forwarded::incrementAndGet);
    private DfQuotaRateLimitGlobalFilter filter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
        ReactiveValueOperations<String, String> values = mock(ReactiveValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.increment(anyString())).thenAnswer(call -> Mono.fromSupplier(() -> counters
                .computeIfAbsent(withoutSecond(call.getArgument(0)), k -> new AtomicLong()).incrementAndGet()));
        when(redis.expire(anyString(), any(Duration.class))).thenReturn(Mono.just(true));

        ReactiveCircuitBreaker breaker = mock(ReactiveCircuitBreaker.class);
        when(breaker.run(any(Mono.class), any(Function.class))).thenAnswer(call -> ((Mono<Object>) call.getArgument(0))
                .onErrorResume((Function<Throwable, Mono<Object>>) call.getArgument(1)));
        ReactiveCircuitBreakerFactory<?, ?> breakers = mock(ReactiveCircuitBreakerFactory.class);
        when(breakers.create("bil-quota")).thenReturn(breaker);

        filter = new DfQuotaRateLimitGlobalFilter(WebClient.builder(), breakers, redis,
                "http://127.0.0.1:" + BIL.getAddress().getPort(), MEMBER_REQ_PER_SEC);
    }

    @AfterAll
    static void stopBil() {
        BIL.stop(0);
    }

    @Test
    void anApiClientIsRefusedAboveThePlansQuota() {
        for (int i = 0; i < FREE_REQ_PER_SEC; i++) {
            assertEquals(HttpStatus.OK, call(apiClient("df-1")));
        }
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, call(apiClient("df-1")));
        assertEquals(FREE_REQ_PER_SEC, forwarded.get());
    }

    @Test
    void aConsoleMemberHasABucketOfTheirOwnAboveThePlansQuota() {
        for (int i = 0; i < MEMBER_REQ_PER_SEC; i++) {
            assertEquals(HttpStatus.OK, call(member("df-1", "alice", "DF_OWNER")));
        }
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, call(member("df-1", "alice", "DF_OWNER")));
        // another person at the same fiduciary is unaffected
        assertEquals(HttpStatus.OK, call(member("df-1", "bob", "DF_AUDITOR")));
    }

    @Test
    void consoleMembersDoNotSpendTheSystemsQuota() {
        for (int i = 0; i < MEMBER_REQ_PER_SEC; i++) {
            call(member("df-1", "alice", "DF_DEVELOPER"));
        }
        for (int i = 0; i < FREE_REQ_PER_SEC; i++) {
            assertEquals(HttpStatus.OK, call(apiClient("df-1")));
        }
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, call(apiClient("df-1")));
    }

    @Test
    void everyFiduciaryCallStillCountsTowardsMonthlyUsage() {
        call(member("df-1", "alice", "DF_OWNER"));
        call(apiClient("df-1"));
        long usage = counters.entrySet().stream().filter(e -> e.getKey().startsWith("usg:df-1:"))
                .mapToLong(e -> e.getValue().get()).sum();
        assertEquals(2, usage);
    }

    @Test
    void aFiduciaryRequestIsForwardedOnceAndARefusedOneNever() {
        call(apiClient("df-1"));
        assertEquals(1, forwarded.get());
        for (int i = 1; i < FREE_REQ_PER_SEC; i++) {
            call(apiClient("df-1"));
        }
        call(apiClient("df-1"));
        assertEquals(FREE_REQ_PER_SEC, forwarded.get());
    }

    @Test
    void aCallerWithoutAFiduciaryPassesUnlimitedAndOnce() {
        Jwt dp = jwt("http://localhost:8080/realms/cm-dp", "dp-1", null, List.of("DP"));
        for (int i = 0; i < 10; i++) {
            assertEquals(HttpStatus.OK, call(dp));
        }
        assertEquals(10, forwarded.get());
        assertEquals(Map.of(), Map.copyOf(counters));
    }

    private HttpStatus call(Jwt jwt) {
        MockServerWebExchange exchange = MockServerWebExchange.builder(MockServerHttpRequest.get("/api/df-reg/dfs/df-1"))
                .principal(new JwtAuthenticationToken(jwt)).build();
        filter.filter(exchange, chain).block(Duration.ofSeconds(5));
        HttpStatus status = (HttpStatus) exchange.getResponse().getStatusCode();
        return status == null ? HttpStatus.OK : status;
    }

    private static Jwt apiClient(String dfId) {
        return jwt(CM_DF, "service-account-" + dfId, dfId, List.of("DF"));
    }

    private static Jwt member(String dfId, String subject, String role) {
        return jwt(CM_DF, subject, dfId, List.of("DF", role));
    }

    private static Jwt jwt(String issuer, String subject, String dfId, List<String> roles) {
        Jwt.Builder builder = Jwt.withTokenValue("t").header("alg", "none").issuer(issuer).subject(subject)
                .claim("roles", roles);
        return (dfId == null ? builder : builder.claim("df_id", dfId)).build();
    }

    private static String withoutSecond(String key) {
        return key.startsWith("ratelimit:") ? key.replaceFirst(":\\d+$", "") : key;
    }

    private static HttpServer freePlanBil() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/internal/dfs/", exchange -> {
                byte[] body = ("{\"dfSubSts\":\"ACTIVE\",\"curPeriodUsg\":0,\"reqPerMonthQta\":10000,\"reqPerSecQta\":"
                        + FREE_REQ_PER_SEC + "}").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
