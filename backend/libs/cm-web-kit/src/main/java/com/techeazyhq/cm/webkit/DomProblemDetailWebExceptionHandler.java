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

import java.net.URI;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Maps a {@link DomRuleViolation} to a {@code application/problem+json} response, so every
 * business-rule rejection in this platform (subscription-inactive, quota-exceeded, etc. —
 * plan §7b) carries a machine-readable {@code type} instead of a bare status code. Ordered
 * ahead of Spring's default handler.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DomProblemDetailWebExceptionHandler implements WebExceptionHandler {

    private final ObjectMapper objectMapper;

    public DomProblemDetailWebExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (!(ex instanceof DomRuleViolation violation)) {
            return Mono.error(ex);
        }

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.valueOf(violation.httpStatus()));
        problem.setType(URI.create("urn:cm:problem:" + violation.problemType()));
        problem.setTitle(violation.problemType());
        problem.setDetail(violation.getMessage());

        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.valueOf(violation.httpStatus()));
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);

        return Mono.fromCallable(() -> objectMapper.writeValueAsBytes(problem))
                .map(bytes -> response.bufferFactory().wrap(bytes))
                .flatMap(buffer -> response.writeWith(Mono.just((DataBuffer) buffer)));
    }
}
