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

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

import org.springframework.cloud.gateway.route.Route;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import com.techeazyhq.cm.reskit.DependencyUnavailableException;

import reactor.core.publisher.Mono;

/**
 * Where every route's circuit breaker (ADR-016, {@code default-filters} in application.yml) forwards a request the
 * service behind it could not take: it was unreachable, timed out, or its breaker is open. Answered by cm-res-kit as
 * 503 problem+json with {@code Retry-After}, naming the route, instead of a bare 500 or a hung connection.
 */
@RestController
public class RouteFallbackController {

    static final String PATH = "/fallback";

    @RequestMapping(PATH)
    public Mono<Void> unavailable(ServerWebExchange exchange) {
        Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
        Throwable cause = exchange.getAttribute(CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR);
        return Mono.error(new DependencyUnavailableException(route != null ? route.getId() : "upstream", cause));
    }
}
