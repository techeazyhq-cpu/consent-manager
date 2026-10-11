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
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.sun.net.httpserver.HttpServer;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

/**
 * The gateway's circuit breakers (ADR-016) with its real configuration: services that are down behind the routes, and
 * a stub PDP that allows every verification.
 */
@SpringBootTest
@AutoConfigureWebTestClient
class GwCircuitBreakerTest {

    private static final HttpServer ALLOWING_PDP = allowingPdp();
    private static final int DOWN = unusedPort();

    @Autowired
    private WebTestClient client;

    @Autowired
    private CircuitBreakerRegistry circuitBreakers;

    @DynamicPropertySource
    static void routesToServicesThatAreDown(DynamicPropertyRegistry registry) {
        // the whole route list, since one indexed entry from a higher source replaces the YAML list
        registry.add("spring.cloud.gateway.routes[0].id", () -> "df-reg");
        registry.add("spring.cloud.gateway.routes[0].uri", () -> "http://127.0.0.1:" + DOWN);
        registry.add("spring.cloud.gateway.routes[0].predicates[0]", () -> "Path=/api/df-reg/**");
        registry.add("spring.cloud.gateway.routes[1].id", () -> "cns-qry");
        registry.add("spring.cloud.gateway.routes[1].uri", () -> "http://127.0.0.1:" + DOWN);
        registry.add("spring.cloud.gateway.routes[1].predicates[0]", () -> "Path=/api/cns-qry/**");
        registry.add("cm.pdp.base-url", () -> "http://127.0.0.1:" + ALLOWING_PDP.getAddress().getPort());
    }

    @AfterAll
    static void stopPdp() {
        ALLOWING_PDP.stop(0);
    }

    @Test
    void aServiceThatIsDownIsAnswered503NamingTheRoute() {
        client.mutateWith(mockJwt()).get().uri("/api/df-reg/dfs/df-1")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectHeader().valueEquals("Retry-After", "30")
                .expectBody()
                .jsonPath("$.type").isEqualTo("urn:cm:problem:dependency-unavailable")
                .jsonPath("$.detail").isEqualTo("df-reg is unavailable, try again shortly");
    }

    @Test
    void anAllowedVerificationWhoseServiceIsDownIs503NotADeny() {
        client.mutateWith(mockJwt()).get().uri("/api/cns-qry/cns-arts/art-1/verify")
                .header("X-Cns-Tkn", "token")
                .exchange()
                .expectStatus().isEqualTo(503);
    }

    @Test
    void aRouteBreakerOpensAfterRepeatedFailuresAndThenRefusesWithoutCalling() {
        CircuitBreaker breaker = circuitBreakers.circuitBreaker("df-reg");
        for (int i = 0; i < 10; i++) {
            client.mutateWith(mockJwt()).get().uri("/api/df-reg/dfs/df-1").exchange().expectStatus().isEqualTo(503);
        }

        assertEquals(CircuitBreaker.State.OPEN, breaker.getState());
        long refusedBefore = breaker.getMetrics().getNumberOfNotPermittedCalls();
        client.mutateWith(mockJwt()).get().uri("/api/df-reg/dfs/df-1").exchange().expectStatus().isEqualTo(503);
        assertEquals(refusedBefore + 1, breaker.getMetrics().getNumberOfNotPermittedCalls());
    }

    private static HttpServer allowingPdp() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                byte[] body = "{\"result\":true}".getBytes(StandardCharsets.UTF_8);
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

    private static int unusedPort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
