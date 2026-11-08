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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.http.HttpStatus;

import reactor.core.publisher.Mono;

class SecurityHeadersWebFilterTest {

    private final SecurityHeadersWebFilter filter = new SecurityHeadersWebFilter();

    @Test
    void everyResponseCarriesTheHeadersEvenAnErrorOne() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/anything"));
        filter.filter(exchange, ex -> {
            ex.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return ex.getResponse().setComplete();
        }).block();

        var h = exchange.getResponse().getHeaders();
        assertThat(h.getFirst("Strict-Transport-Security")).isEqualTo("max-age=31536000; includeSubDomains");
        assertThat(h.getFirst("Content-Security-Policy")).isEqualTo("default-src 'none'; frame-ancestors 'none'");
        assertThat(h.getFirst("Permissions-Policy")).contains("camera=()");
        assertThat(h.getFirst("Cross-Origin-Opener-Policy")).isEqualTo("same-origin");
    }

    @Test
    void aHeaderAServiceAlreadySetIsNotOverwritten() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/anything"));
        filter.filter(exchange, ex -> {
            ex.getResponse().getHeaders().set("Content-Security-Policy", "default-src 'self'");
            return Mono.empty();
        }).block();
        exchange.getResponse().setComplete().block();

        assertThat(exchange.getResponse().getHeaders().getFirst("Content-Security-Policy")).isEqualTo("default-src 'self'");
        assertThat(exchange.getResponse().getHeaders().getFirst("Strict-Transport-Security")).isNotNull();
    }
}
