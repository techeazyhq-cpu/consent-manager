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

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;

import com.techeazyhq.cm.seckit.AuthCtx;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import reactor.core.publisher.Mono;

/**
 * The gateway acting as the Policy Enforcement Point (plan §9, Stage 3 Dynamic Enforcement):
 * intercepts {@code GET /api/cns-qry/cns-arts/{id}/verify}, extracts the caller-presented
 * {@code CnsTkn} header, delegates the decision to {@code cm-pdp} (OPA), and only forwards the request downstream on
 * an allow. A deny is a 403. A check that could not be made (OPA unreachable, slow or behind an open breaker, or OPA
 * unable to reach what it checks against) is a 503 with {@code Retry-After}: never an allow, and never a deny either
 * (ADR-057, VAPT V-17). The caller's own fiduciary id goes to the policy too, which only honours a token for the
 * fiduciary it names (ADR-006).
 */
@Component
public class PepVerifyGatewayFilter implements GlobalFilter, Ordered {

    private static final Pattern VERIFY_PATH = Pattern.compile("^/api/cns-qry/cns-arts/([^/]+)/verify$");
    private static final String CNS_TKN_HEADER = "X-Cns-Tkn";
    private static final byte[] DENY_BODY = "{\"type\":\"urn:cm:problem:consent-not-verified\",\"title\":\"DENY\"}"
            .getBytes(StandardCharsets.UTF_8);
    private static final byte[] UNAVAILABLE_BODY = ("{\"type\":\"urn:cm:problem:consent-check-unavailable\","
            + "\"title\":\"consent-check-unavailable\",\"status\":503,"
            + "\"detail\":\"The consent could not be checked right now. This is not a decision: try again.\"}")
            .getBytes(StandardCharsets.UTF_8);
    /** After a timeout, or a PDP that couldn't check: try again in a moment. */
    private static final Duration RETRY_SOON = Duration.ofSeconds(1);

    /** What the PDP said, or that it couldn't say. */
    enum Decision { ALLOW, DENY, UNDETERMINED }

    private final WebClient opaWebClient;
    private final ReactiveCircuitBreaker pdpCircuitBreaker;
    private final Duration breakerOpenWait;

    public PepVerifyGatewayFilter(WebClient.Builder builder, ReactiveCircuitBreakerFactory<?, ?> cbFactory,
                                  @Value("${cm.pdp.base-url}") String pdpBaseUrl,
                                  @Value("${resilience4j.circuitbreaker.configs.default.wait-duration-in-open-state:30s}")
                                  Duration breakerOpenWait) {
        this.opaWebClient = builder.baseUrl(pdpBaseUrl).build();
        this.pdpCircuitBreaker = cbFactory.create("pdp");
        this.breakerOpenWait = breakerOpenWait;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        Matcher matcher = VERIFY_PATH.matcher(path);
        if (!matcher.matches()) {
            return chain.filter(exchange);
        }

        String cnsArtId = matcher.group(1);
        String cnsTkn = exchange.getRequest().getHeaders().getFirst(CNS_TKN_HEADER);
        String purpose = exchange.getRequest().getQueryParams().getFirst("purpose");
        String attribute = exchange.getRequest().getQueryParams().getFirst("attribute");

        if (cnsTkn == null) {
            return deny(exchange);
        }

        return callerDfId(exchange)
                .flatMap(dfId -> decide(cnsArtId, cnsTkn, dfId, purpose, attribute))
                .flatMap(outcome -> switch (outcome.decision()) {
                    case ALLOW -> chain.filter(exchange);
                    case DENY -> deny(exchange);
                    case UNDETERMINED -> unavailable(exchange, outcome.retryAfter());
                });
    }

    /**
     * The PDP's decision, behind a circuit breaker (ADR-016). Fails closed, and says so: an unreachable, failing, slow
     * or open-circuit PDP, an answer without a decision, and a PDP that couldn't reach what it checks against are all
     * undetermined, never an allow (ADR-057). Only the decision is guarded, so an error from the service behind the
     * gateway after an allow reaches the caller as that error, not as an unavailable check.
     */
    private Mono<Outcome> decide(String cnsArtId, String cnsTkn, String dfId, String purpose, String attribute) {
        Mono<Outcome> decision = opaWebClient.post()
                .uri("/v1/data/consent/authorization/decision")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("input", Map.of(
                        "action", "READ",
                        "cns_art_id", cnsArtId,
                        "consent_token", cnsTkn,
                        "caller_df_id", dfId,
                        "requested_purpose", purpose == null ? "" : purpose,
                        "requested_attribute", attribute == null ? "" : attribute)))
                .retrieve()
                .bodyToMono(OpaResp.class)
                .map(resp -> new Outcome(of(resp.result()), RETRY_SOON))
                .defaultIfEmpty(new Outcome(Decision.UNDETERMINED, RETRY_SOON));
        return pdpCircuitBreaker.run(decision, err -> Mono.just(new Outcome(Decision.UNDETERMINED,
                err instanceof CallNotPermittedException ? breakerOpenWait : RETRY_SOON)));
    }

    /** OPA's {@code decision}: an allow only when it says so; no decision at all (no policy loaded) is undetermined. */
    static Decision of(PdpDecision result) {
        if (result == null || result.allow() == null) {
            return Decision.UNDETERMINED;
        }
        if (Boolean.TRUE.equals(result.allow())) {
            return Decision.ALLOW;
        }
        return Boolean.TRUE.equals(result.undetermined()) ? Decision.UNDETERMINED : Decision.DENY;
    }

    /**
     * The fiduciary making the call, from its own access token ({@code df_id}, ADR-008). A token is only honoured for
     * the fiduciary it was issued to, so a caller without a fiduciary identity gets an empty id and is denied.
     */
    private static Mono<String> callerDfId(ServerWebExchange exchange) {
        return exchange.getPrincipal()
                .filter(JwtAuthenticationToken.class::isInstance)
                // only the fiduciaries' realm says which fiduciary a caller is (ADR-035)
                .flatMap(principal -> Mono.justOrEmpty(AuthCtx.from(((JwtAuthenticationToken) principal).getToken()).dfId()))
                .defaultIfEmpty("");
    }

    /** A 403 problem: the PDP decided, and the answer is no. */
    private static Mono<Void> deny(ServerWebExchange exchange) {
        return problem(exchange, HttpStatus.FORBIDDEN, DENY_BODY, null);
    }

    /** A 503 problem with Retry-After: the consent couldn't be checked, which says nothing about whether it stands. */
    private static Mono<Void> unavailable(ServerWebExchange exchange, Duration retryAfter) {
        return problem(exchange, HttpStatus.SERVICE_UNAVAILABLE, UNAVAILABLE_BODY, retryAfter);
    }

    /**
     * Writes a problem, unless this exchange's response has already gone out: then its headers are read-only, and
     * writing them threw an UnsupportedOperationException that logged an ERROR after the caller already had its answer
     * (#218, thousands a minute under load). What was sent stands.
     */
    private static Mono<Void> problem(ServerWebExchange exchange, HttpStatus status, byte[] body, Duration retryAfter) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.empty();
        }
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        if (retryAfter != null) {
            exchange.getResponse().getHeaders().set("Retry-After", Long.toString(Math.max(1, retryAfter.toSeconds())));
        }
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private record Outcome(Decision decision, Duration retryAfter) {
    }

    /** OPA's {@code data.consent.authorization.decision}. */
    record PdpDecision(Boolean allow, Boolean undetermined) {
    }

    private record OpaResp(PdpDecision result) {
    }
}
