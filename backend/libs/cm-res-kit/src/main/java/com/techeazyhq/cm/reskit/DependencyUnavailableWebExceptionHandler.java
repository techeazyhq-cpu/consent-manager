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

package com.techeazyhq.cm.reskit;

import java.net.URI;
import java.time.Duration;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;

import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Mono;

/**
 * Answers a {@link DependencyUnavailableException} with 503 {@code application/problem+json} of type
 * {@code urn:cm:problem:dependency-unavailable} and a {@code Retry-After} of the breakers' open-state wait, so clients
 * back off for as long as the breaker will refuse calls anyway.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DependencyUnavailableWebExceptionHandler implements WebExceptionHandler {

    static final String PROBLEM_TYPE = "dependency-unavailable";

    private final ObjectMapper objectMapper;
    private final Duration retryAfter;

    public DependencyUnavailableWebExceptionHandler(ObjectMapper objectMapper, Duration retryAfter) {
        this.objectMapper = objectMapper;
        this.retryAfter = retryAfter;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (!(ex instanceof DependencyUnavailableException unavailable)) {
            return Mono.error(ex);
        }

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.SERVICE_UNAVAILABLE);
        problem.setType(URI.create("urn:cm:problem:" + PROBLEM_TYPE));
        problem.setTitle(PROBLEM_TYPE);
        problem.setDetail(unavailable.dependency() + " is unavailable, try again shortly");

        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        response.getHeaders().set("Retry-After", Long.toString(Math.max(1, retryAfter.toSeconds())));

        return Mono.fromCallable(() -> objectMapper.writeValueAsBytes(problem))
                .map(bytes -> response.bufferFactory().wrap(bytes))
                .flatMap(buffer -> response.writeWith(Mono.just((DataBuffer) buffer)));
    }
}
