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

package com.techeazyhq.cm.webkit;

import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Propagates {@code X-Correlation-Id} across every service call (plan §9): reuses an inbound header if it is a plain
 * token, otherwise mints one; passes it on to whatever this call goes on to call, so the gateway and the service share
 * one id; sends it back as the only value of the response header; and exposes it to downstream code via the Reactor
 * context. It is also in every error response ({@link Problems}) and every {@code [CM-...]} log line a caller may
 * quote, so it is never taken as sent: only letters, digits and {@code . _ : -}, at most 64 of them, so it can't carry a
 * line break into a log or an odd string into a body.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdWebFilter implements WebFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String CONTEXT_KEY = "correlationId";
    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9._:-]{1,64}");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String sent = request.getHeaders().getFirst(HEADER);
        boolean reused = sent != null && SAFE.matcher(sent).matches();
        String correlationId = reused ? sent : UUID.randomUUID().toString();

        // set now, for the handlers that answer an error (they see this exchange, not the one passed down)...
        exchange.getResponse().getHeaders().set(HEADER, correlationId);
        // ...and again as the response is committed: a service behind the gateway answers with its own copy, and the
        // caller must get exactly one
        exchange.getResponse().beforeCommit(() -> {
            exchange.getResponse().getHeaders().set(HEADER, correlationId);
            return Mono.empty();
        });
        // what this call calls next (the gateway's proxied request) carries the same id
        ServerWebExchange next = reused && sent.equals(correlationId) && request.getHeaders().get(HEADER).size() == 1
                ? exchange
                : exchange.mutate().request(r -> r.headers(h -> h.set(HEADER, correlationId))).build();
        return chain.filter(next).contextWrite(ctx -> ctx.put(CONTEXT_KEY, correlationId));
    }
}
