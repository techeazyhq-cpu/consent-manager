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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.techeazyhq.cm.webkit.Problems;

import reactor.core.publisher.Mono;

/**
 * Limits how often one client can use the public sign-up pages (ADR-065), which need no account: by default ten requests an
 * hour from one address, in a Redis counter. Applies to {@code POST /api/df-reg/signup} and {@code .../signup/verify} only.
 * The client is the connection's address; {@code X-Forwarded-For} is only believed when {@code cm.rate-limit.trust-forwarded-for}
 * is set, for a deployment where a proxy the platform controls sets it, since anyone else could write any value there.
 * Fails open if Redis is unreachable: df-reg still limits each address and the whole platform.
 */
@Component
public class SignupRateLimitGlobalFilter implements GlobalFilter, Ordered {

    private static final Duration WINDOW = Duration.ofHours(1);

    private final ReactiveStringRedisTemplate redis;
    private final long perHour;
    private final boolean trustForwardedFor;

    public SignupRateLimitGlobalFilter(ReactiveStringRedisTemplate redis,
                                       @Value("${cm.rate-limit.signup-per-hour:10}") long perHour,
                                       @Value("${cm.rate-limit.trust-forwarded-for:false}") boolean trustForwardedFor) {
        this.redis = redis;
        this.perHour = perHour;
        this.trustForwardedFor = trustForwardedFor;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 4;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (exchange.getRequest().getMethod() != HttpMethod.POST
                || !("/api/df-reg/signup".equals(path) || "/api/df-reg/signup/verify".equals(path))) {
            return chain.filter(exchange);
        }
        String key = "ratelimit:signup:" + client(exchange) + ":" + (Instant.now().getEpochSecond() / WINDOW.toSeconds());
        // decided before forwarding, never with switchIfEmpty after it: the chain's Mono<Void> always completes empty
        return redis.opsForValue().increment(key)
                .flatMap(count -> {
                    if (count == 1) {
                        redis.expire(key, WINDOW.plusMinutes(1)).subscribe();
                    }
                    return count > perHour ? tooManyRequests(exchange) : chain.filter(exchange);
                })
                .onErrorResume(e -> e instanceof org.springframework.data.redis.RedisConnectionFailureException ? chain.filter(exchange)
                        : Mono.error(e));
    }

    private String client(ServerWebExchange exchange) {
        if (trustForwardedFor) {
            String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
        }
        var remote = exchange.getRequest().getRemoteAddress();
        return remote == null || remote.getAddress() == null ? "unknown" : remote.getAddress().getHostAddress();
    }

    private Mono<Void> tooManyRequests(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        exchange.getResponse().getHeaders().add("Retry-After", String.valueOf(WINDOW.toSeconds()));
        byte[] body = Problems.json("signup-rate-limited", 429, "Too many sign-up requests from this address. Try again later.",
                Problems.correlationId(exchange));
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
