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
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
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

import reactor.core.publisher.Mono;

/**
 * Per-DF token-bucket-ish rate limiting (plan §9/§11): resolves {@code reqPerSecQta} from
 * cm-bil-svc's subscription quota (short-TTL local cache, avoids a synchronous call on every
 * request), then checks a Redis fixed-window counter per second. This is a burst-rate check,
 * distinct from cm-cns-cmd-svc's own hard monthly/status gate (plan §7b) — both read the
 * same underlying DfSub/UsgCtr data but enforce at different points for different reasons.
 */
@Component
public class DfQuotaRateLimitGlobalFilter implements GlobalFilter, Ordered {

    private static final Duration QUOTA_CACHE_TTL = Duration.ofSeconds(10);
    private static final DateTimeFormatter USAGE_PERIOD_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final WebClient bilWebClient;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ConcurrentHashMap<String, CachedQuota> quotaCache = new ConcurrentHashMap<>();

    public DfQuotaRateLimitGlobalFilter(WebClient.Builder builder, ReactiveStringRedisTemplate redisTemplate,
                                         @Value("${cm.service-urls.bil}") String bilBaseUrl) {
        this.bilWebClient = builder.baseUrl(bilBaseUrl).build();
        this.redisTemplate = redisTemplate;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 5;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Mono#map's function may never return null (Reactor throws NPE) — a CM_ADMIN or DP
        // token has no df_id claim at all, so this must be flatMap + Mono.justOrEmpty, not
        // map + a null check downstream. Discovered and fixed during Phase 1 verification.
        return exchange.getPrincipal()
                .cast(JwtAuthenticationToken.class)
                .flatMap(token -> Mono.justOrEmpty(((Jwt) token.getPrincipal()).getClaimAsString("df_id")))
                .flatMap(dfId -> checkAndForward(exchange, chain, dfId))
                .switchIfEmpty(chain.filter(exchange));
    }

    private Mono<Void> checkAndForward(ServerWebExchange exchange, GatewayFilterChain chain, String dfId) {
        // Billing usage (plan §11 step 1: "every DF-authenticated cm-gw request increments
        // usg:{dfId}:{yyyy-MM}") was never actually wired to this counter — cm-bil-svc's usage
        // view and monthly-quota gate always read 0 regardless of real traffic. Fire-and-forget,
        // same as the rate-limit key below: billing usage must never block/slow the request path.
        String usageKey = "usg:" + dfId + ":" + LocalDate.now(ZoneOffset.UTC).format(USAGE_PERIOD_FORMAT);
        redisTemplate.opsForValue().increment(usageKey).subscribe();

        return quotaFor(dfId).flatMap(reqPerSecQta -> {
            String key = "ratelimit:" + dfId + ":" + Instant.now().getEpochSecond();
            return redisTemplate.opsForValue().increment(key)
                    .flatMap(count -> {
                        redisTemplate.expire(key, Duration.ofSeconds(2)).subscribe();
                        return count > reqPerSecQta ? tooManyRequests(exchange) : chain.filter(exchange);
                    });
        });
    }

    private Mono<Long> quotaFor(String dfId) {
        CachedQuota cached = quotaCache.get(dfId);
        if (cached != null && cached.fetchedAt().plus(QUOTA_CACHE_TTL).isAfter(Instant.now())) {
            return Mono.just(cached.reqPerSecQta());
        }
        return bilWebClient.get()
                .uri("/internal/dfs/{id}/sub-eligibility", dfId)
                .retrieve()
                .bodyToMono(SubEligResp.class)
                .map(resp -> {
                    quotaCache.put(dfId, new CachedQuota(resp.reqPerSecQta(), Instant.now()));
                    return resp.reqPerSecQta();
                })
                .onErrorReturn(Long.MAX_VALUE); // cm-bil-svc unreachable -> don't block traffic on a resilience dependency's outage
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
