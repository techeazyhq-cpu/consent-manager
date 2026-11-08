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

import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

/**
 * Headers every response from the public edge carries, including the ones Spring Security answers itself (a 401 or a 403)
 * and the ones a route forwards from a service (ADR-073). Spring Security adds {@code Strict-Transport-Security} only to a
 * request it sees as secure, and TLS ends at the ingress, so the gateway sets it itself: a browser ignores it over plain HTTP
 * and obeys it over HTTPS. The API returns JSON and nothing that should ever be framed, scripted or embedded, so its policy is
 * the strictest one. A header a route or service already set is left alone.
 */
@Component
public class SecurityHeadersWebFilter implements WebFilter, Ordered {

    static final String HSTS = "max-age=31536000; includeSubDomains";
    static final String CSP = "default-src 'none'; frame-ancestors 'none'";
    static final String PERMISSIONS = "camera=(), microphone=(), geolocation=(), payment=()";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        exchange.getResponse().beforeCommit(() -> {
            HttpHeaders h = exchange.getResponse().getHeaders();
            h.putIfAbsent("Strict-Transport-Security", java.util.List.of(HSTS));
            h.putIfAbsent("Content-Security-Policy", java.util.List.of(CSP));
            h.putIfAbsent("Permissions-Policy", java.util.List.of(PERMISSIONS));
            h.putIfAbsent("Cross-Origin-Opener-Policy", java.util.List.of("same-origin"));
            return Mono.empty();
        });
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
