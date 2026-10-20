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

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;

import com.techeazyhq.cm.seckit.AuthCtx;

import reactor.core.publisher.Mono;

/**
 * Per-DF token-bucket-ish rate limiting (plan §9/§11): resolves {@code reqPerSecQta} from
 * cm-bil-svc's subscription quota (short-TTL local cache, avoids a synchronous call on every
 * request), then checks a Redis fixed-window counter per second. This is a burst-rate check,
 * distinct from cm-cns-cmd-svc's own hard monthly/status gate (plan §7b) — both read the
 * same underlying DfSub/UsgCtr data but enforce at different points for different reasons.
 *
 * <p>The plan's per-second quota is for the fiduciary's systems (API clients and API keys). Its people in the DF
 * Console each get their own bucket at {@code cm.rate-limit.member-req-per-sec} instead (ADR-048); their calls still
 * count towards the monthly usage (ADR-039).
 */
@Component
public class DfQuotaRateLimitGlobalFilter implements GlobalFilter, Ordered {

    private static final Duration QUOTA_CACHE_TTL = Duration.ofSeconds(10);
    private static final DateTimeFormatter USAGE_PERIOD_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final WebClient bilWebClient;
    private final ReactiveCircuitBreaker bilCircuitBreaker;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ConcurrentHashMap<String, CachedQuota> quotaCache = new ConcurrentHashMap<>();
    private final long memberReqPerSec;

    public DfQuotaRateLimitGlobalFilter(WebClient.Builder builder, ReactiveCircuitBreakerFactory<?, ?> cbFactory,
                                         ReactiveStringRedisTemplate redisTemplate,
                                         @Value("${cm.service-urls.bil}") String bilBaseUrl,
                                         @Value("${cm.rate-limit.member-req-per-sec:20}") long memberReqPerSec) {
        this.bilWebClient = builder.baseUrl(bilBaseUrl).build();
        this.bilCircuitBreaker = cbFactory.create("bil-quota");
        this.redisTemplate = redisTemplate;
        this.memberReqPerSec = memberReqPerSec;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 5;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // A CM_ADMIN or DP token has no df_id (only the fiduciaries' realm says which fiduciary a caller is, ADR-035)
        // and passes unlimited. Decided before forwarding, never with switchIfEmpty after it: the chain's Mono<Void>
        // always completes empty, so that would forward a fiduciary's request a second time, even after a 429.
        return exchange.getPrincipal()
                .cast(JwtAuthenticationToken.class)
                .map(token -> AuthCtx.from((Jwt) token.getPrincipal()))
                .filter(AuthCtx::isDf)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .flatMap(auth -> auth.isPresent() ? checkAndForward(exchange, chain, auth.get()) : chain.filter(exchange));
    }

    private Mono<Void> checkAndForward(ServerWebExchange exchange, GatewayFilterChain chain, AuthCtx auth) {
        String dfId = auth.dfId();
        // Billing usage (plan §11 step 1: "every DF-authenticated cm-gw request increments
        // usg:{dfId}:{yyyy-MM}") was never actually wired to this counter — cm-bil-svc's usage
        // view and monthly-quota gate always read 0 regardless of real traffic. Fire-and-forget,
        // same as the rate-limit key below: billing usage must never block/slow the request path.
        String usageKey = "usg:" + dfId + ":" + LocalDate.now(ZoneOffset.UTC).format(USAGE_PERIOD_FORMAT);
        redisTemplate.opsForValue().increment(usageKey).subscribe();

        // A person in the DF Console (ADR-048) has a bucket of their own at a fixed rate, so a page load's handful of
        // calls neither hits the plan's per-second limit nor spends the fiduciary's systems' share of it.
        long epochSecond = Instant.now().getEpochSecond();
        if (auth.isDfMember()) {
            return withinLimit(exchange, chain,
                    "ratelimit:member:" + dfId + ":" + auth.subject() + ":" + epochSecond, memberReqPerSec);
        }
        return quotaFor(dfId).flatMap(reqPerSecQta ->
                withinLimit(exchange, chain, "ratelimit:" + dfId + ":" + epochSecond, reqPerSecQta));
    }

    private Mono<Void> withinLimit(ServerWebExchange exchange, GatewayFilterChain chain, String key, long limit) {
        return redisTemplate.opsForValue().increment(key)
                .flatMap(count -> {
                    redisTemplate.expire(key, Duration.ofSeconds(2)).subscribe();
                    return count > limit ? tooManyRequests(exchange) : chain.filter(exchange);
                });
    }

    private Mono<Long> quotaFor(String dfId) {
        CachedQuota cached = quotaCache.get(dfId);
        if (cached != null && cached.fetchedAt().plus(QUOTA_CACHE_TTL).isAfter(Instant.now())) {
            return Mono.just(cached.reqPerSecQta());
        }
        Mono<Long> lookup = bilWebClient.get()
                .uri("/internal/dfs/{id}/sub-eligibility", dfId)
                .retrieve()
                .bodyToMono(SubEligResp.class)
                .map(resp -> {
                    quotaCache.put(dfId, new CachedQuota(resp.reqPerSecQta(), Instant.now()));
                    return resp.reqPerSecQta();
                });
        // Fails open (ADR-016): the burst limit protects capacity, it is not an authorisation, so an unreachable,
        // failing or open-circuit cm-bil-svc must not block traffic. Keep the last quota known for this DF, if any;
        // cm-cns-cmd-svc still enforces the monthly quota itself, failing closed.
        return bilCircuitBreaker.run(lookup, err -> Mono.just(cached != null ? cached.reqPerSecQta() : Long.MAX_VALUE));
    }

    private Mono<Void> tooManyRequests(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        exchange.getResponse().getHeaders().add("Retry-After", "1");
        byte[] body = "{\"type\":\"urn:cm:problem:rate-limit-exceeded\",\"title\":\"Too Many Requests\"}".getBytes();
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private record CachedQuota(long reqPerSecQta, Instant fetchedAt) {
    }

    private record SubEligResp(String dfSubSts, long curPeriodUsg, long reqPerMonthQta, long reqPerSecQta) {
    }
}
