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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigurationProperties;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.reactive.function.client.WebClient;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import com.sun.net.httpserver.HttpServer;
import com.techeazyhq.cm.seckit.CnsTknClaims;
import com.techeazyhq.cm.seckit.CnsTknKeys;
import com.techeazyhq.cm.seckit.CnsTknSigner;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import reactor.core.publisher.Mono;

/**
 * The gateway as PEP against a real OPA running the repository's {@code cns_authz.rego} (#60): a consent token is
 * honoured only when signed by cm-cns-cmd-svc, for the calling fiduciary and purpose, and still in force. cm-cns-cmd-svc
 * is a stand-in serving its JWKS and the live status, reached from the OPA container over the host. Also the
 * per-second limit (ADR-048) against a real Redis.
 */
@org.testcontainers.junit.jupiter.Testcontainers
class GwPepPdpIT {

    private static final String CM_DF = "http://localhost:8080/realms/cm-df";
    private static final Map<String, String> LIVE_STATUS = new ConcurrentHashMap<>();
    private static final CnsTknKeys KEYS = keys();
    private static final HttpServer CNS_CMD = standInCnsCmd();

    static {
        Testcontainers.exposeHostPorts(CNS_CMD.getAddress().getPort());
    }

    @Container
    static final GenericContainer<?> OPA = new GenericContainer<>(DockerImageName.parse("openpolicyagent/opa:1.20.1"))
            .withCopyFileToContainer(MountableFile.forHostPath(Path.of("../../../infra/opa/policies/cns_authz.rego")),
                    "/policies/cns_authz.rego")
            .withCopyToContainer(Transferable.of("{\"config\":{\"cns_cmd_base_url\":\"http://host.testcontainers.internal:"
                    + CNS_CMD.getAddress().getPort() + "\"}}"), "/policies/data.json")
            .withCommand("run", "--server", "--addr=0.0.0.0:8181", "/policies")
            .withExposedPorts(8181)
            .waitingFor(Wait.forHttp("/health").forPort(8181));

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private final AtomicInteger forwarded = new AtomicInteger();
    private final GatewayFilterChain chain = exchange -> Mono.fromRunnable(forwarded::incrementAndGet);

    @AfterAll
    static void stopCnsCmd() {
        CNS_CMD.stop(0);
    }

    @Test
    void aTokenForThisFiduciaryPurposeAndAConsentInForceIsHonoured() {
        LIVE_STATUS.put("art-ok", "ISSUED");

        assertEquals(HttpStatus.OK, verify("art-ok", token("art-ok", "df-a", "ORDER_UPDATES", KEYS), "df-a",
                "ORDER_UPDATES"));
        assertEquals(1, forwarded.get());
    }

    @Test
    void aWithdrawnConsentIsDeniedThoughItsTokenHasNotExpired() {
        LIVE_STATUS.put("art-revoked", "REVOKED");

        assertDenied(verify("art-revoked", token("art-revoked", "df-a", "ORDER_UPDATES", KEYS), "df-a",
                "ORDER_UPDATES"));
    }

    @Test
    void aTokenPresentedByAnotherFiduciaryIsDenied() {
        LIVE_STATUS.put("art-other", "ISSUED");

        assertDenied(verify("art-other", token("art-other", "df-a", "ORDER_UPDATES", KEYS), "df-b", "ORDER_UPDATES"));
    }

    @Test
    void aTokenForAnotherPurposeIsDenied() {
        LIVE_STATUS.put("art-purpose", "ISSUED");

        assertDenied(verify("art-purpose", token("art-purpose", "df-a", "ORDER_UPDATES", KEYS), "df-a", "MARKETING"));
    }

    @Test
    void aTokenForAnotherConsentIsDenied() {
        LIVE_STATUS.put("art-1", "ISSUED");
        LIVE_STATUS.put("art-2", "ISSUED");

        assertDenied(verify("art-2", token("art-1", "df-a", "ORDER_UPDATES", KEYS), "df-a", "ORDER_UPDATES"));
    }

    @Test
    void aTokenNotSignedByCmCnsCmdSvcIsDenied() {
        LIVE_STATUS.put("art-forged", "ISSUED");

        assertDenied(verify("art-forged", token("art-forged", "df-a", "ORDER_UPDATES", keys()), "df-a",
                "ORDER_UPDATES"));
    }

    @Test
    void anExpiredTokenIsDenied() {
        LIVE_STATUS.put("art-expired", "ISSUED");
        String expired = new CnsTknSigner(KEYS).sign(new CnsTknClaims("art-expired", "dp-1", "df-a", "ORDER_UPDATES",
                CnsTknClaims.AccessType.ALLOW, List.of("EMAIL"), Instant.now().minusSeconds(60)));

        assertDenied(verify("art-expired", expired, "df-a", "ORDER_UPDATES"));
    }

    @Test
    void aFiduciarysSystemsAboveThePlansRatePerSecondGet429() {
        HttpServer bil = freePlanBil();
        LettuceConnectionFactory redis = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        redis.afterPropertiesSet();
        try {
            DfQuotaRateLimitGlobalFilter quota = new DfQuotaRateLimitGlobalFilter(WebClient.builder(), breakers(),
                    new ReactiveStringRedisTemplate(redis), "http://127.0.0.1:" + bil.getAddress().getPort(), 20);
            int refused = 0;
            // 5 a second: 11 calls in a row span at most two windows, so at least one is refused
            for (int i = 0; i < 11; i++) {
                MockServerWebExchange exchange = MockServerWebExchange.builder(
                                MockServerHttpRequest.get("/api/df-reg/dfs/df-q"))
                        .principal(new JwtAuthenticationToken(jwt("service-account-df-q", "df-q"))).build();
                quota.filter(exchange, chain).block(Duration.ofSeconds(5));
                if (exchange.getResponse().getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                    refused++;
                }
            }
            assertTrue(refused >= 1, "refused " + refused);
            assertEquals(11 - refused, forwarded.get(), "a refused call is never forwarded");
        } finally {
            redis.destroy();
            bil.stop(0);
        }
    }


    private HttpStatus verify(String cnsArtId, String cnsTkn, String callerDfId, String purpose) {
        PepVerifyGatewayFilter pep = new PepVerifyGatewayFilter(WebClient.builder(), breakers(),
                "http://" + OPA.getHost() + ":" + OPA.getMappedPort(8181));
        MockServerWebExchange exchange = MockServerWebExchange.builder(MockServerHttpRequest
                        .get("/api/cns-qry/cns-arts/" + cnsArtId + "/verify?purpose=" + purpose)
                        .header("X-Cns-Tkn", cnsTkn))
                .principal(new JwtAuthenticationToken(jwt("service-account-" + callerDfId, callerDfId))).build();
        pep.filter(exchange, chain).block(Duration.ofSeconds(10));
        HttpStatus status = (HttpStatus) exchange.getResponse().getStatusCode();
        return status == null ? HttpStatus.OK : status;
    }

    private void assertDenied(HttpStatus status) {
        assertEquals(HttpStatus.FORBIDDEN, status);
        assertEquals(0, forwarded.get(), "a denied verification is never forwarded");
    }

    private static String token(String cnsArtId, String dfId, String purpose, CnsTknKeys keys) {
        return new CnsTknSigner(keys).sign(new CnsTknClaims(cnsArtId, "dp-1", dfId, purpose,
                CnsTknClaims.AccessType.ALLOW, List.of("EMAIL"), Instant.now().plus(Duration.ofMinutes(15))));
    }

    private static Jwt jwt(String subject, String dfId) {
        return Jwt.withTokenValue("t").header("alg", "none").issuer(CM_DF).subject(subject)
                .claim("roles", List.of("DF")).claim("df_id", dfId).build();
    }

    /** The gateway's own time limits for its calls on the request path (application.yml): 2s, not Resilience4j's 1s. */
    private static ReactiveResilience4JCircuitBreakerFactory breakers() {
        return new ReactiveResilience4JCircuitBreakerFactory(CircuitBreakerRegistry.ofDefaults(),
                TimeLimiterRegistry.of(TimeLimiterConfig.custom().timeoutDuration(Duration.ofSeconds(2)).build()),
                null /* no bulkhead */, new Resilience4JConfigurationProperties());
    }

    private static CnsTknKeys keys() {
        try {
            return CnsTknKeys.loadOrCreate(Files.createTempDirectory("cns-tkn-keys").resolve("jwks.json"), true,
                    Clock.systemUTC());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** cm-cns-cmd-svc's JWKS and live status, the two things the policy asks it for. */
    private static HttpServer standInCnsCmd() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", 0), 0);
            server.createContext("/api/cns-cmd/.well-known/jwks.json",
                    exchange -> respond(exchange, 200, KEYS.publicJwksJson()));
            server.createContext("/internal/cns-arts/", exchange -> {
                String id = exchange.getRequestURI().getPath().split("/")[3];
                String status = LIVE_STATUS.get(id);
                respond(exchange, status == null ? 404 : 200, status == null ? "{}" : "{\"status\":\"" + status + "\"}");
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static HttpServer freePlanBil() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/internal/dfs/", exchange -> respond(exchange, 200,
                    "{\"dfSubSts\":\"ACTIVE\",\"curPeriodUsg\":0,\"reqPerMonthQta\":10000,\"reqPerSecQta\":5}"));
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String json)
            throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
