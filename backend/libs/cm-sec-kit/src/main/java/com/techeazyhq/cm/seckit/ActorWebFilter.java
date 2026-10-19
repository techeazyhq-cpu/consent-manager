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

package com.techeazyhq.cm.seckit;

import org.springframework.core.Ordered;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import com.techeazyhq.cm.audkit.Actor;
import com.techeazyhq.cm.audkit.Actors;

import reactor.core.publisher.Mono;

/**
 * Puts the caller's {@link Actor} into Reactor's context for the rest of the request (ADR-040), so whatever the
 * request changes is recorded as done by them. Runs after Spring Security has authenticated the token; a request
 * without one acts as the system.
 */
public class ActorWebFilter implements WebFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return exchange.getPrincipal()
                .filter(JwtAuthenticationToken.class::isInstance)
                .map(principal -> ActorOf.token((Jwt) ((JwtAuthenticationToken) principal).getPrincipal()))
                .defaultIfEmpty(Actor.SYSTEM)
                .flatMap(actor -> chain.filter(exchange).contextWrite(ctx -> Actors.with(ctx, actor)));
    }

    /** After the security filter chain (order -100), which authenticates the token. */
    @Override
    public int getOrder() {
        return 0;
    }
}
