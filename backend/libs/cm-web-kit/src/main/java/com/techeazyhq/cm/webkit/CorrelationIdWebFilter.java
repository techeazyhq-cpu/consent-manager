package com.techeazyhq.cm.webkit;

import java.util.UUID;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Propagates {@code X-Correlation-Id} across every service call (plan §9): reuses an
 * inbound header if present (set by {@code cm-gw}), otherwise mints one, echoes it on the
 * response, and exposes it to downstream code via the Reactor context.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdWebFilter implements WebFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String CONTEXT_KEY = "correlationId";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String correlationId = request.getHeaders().getFirst(HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        exchange.getResponse().getHeaders().add(HEADER, correlationId);
        String finalCorrelationId = correlationId;
        return chain.filter(exchange).contextWrite(ctx -> ctx.put(CONTEXT_KEY, finalCorrelationId));
    }
}
