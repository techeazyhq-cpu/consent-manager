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

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * What no one else answers (ADR-062), as the same {@code application/problem+json} instead of Spring's default error
 * JSON, which carries a timestamp and an internal request id and varies by version:
 * <ul>
 *   <li>input that could not be read (bad JSON, a parameter of the wrong type): 400 {@code malformed-request};</li>
 *   <li>an exception nobody expected: 500 {@code internal-error}, with nothing about it in the answer, and the exception
 *       logged as {@code [CM-INTERNAL-ERROR]} with the correlation id the caller was given.</li>
 * </ul>
 * It leaves alone what is already someone's to answer: other {@code ResponseStatusException}s (unknown route, wrong
 * method), Spring Security's exceptions, a response already on its way, and a client that went away.
 * Ordered after the handlers for {@link DomRuleViolation} and dependency outages, and ahead of Spring's default.
 */
@Order(-2)
public class FallbackProblemWebExceptionHandler implements WebExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(FallbackProblemWebExceptionHandler.class);

    private final ObjectMapper objectMapper;

    public FallbackProblemWebExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted() || isNotOurs(ex)) {
            return Mono.error(ex);
        }
        if (ex instanceof ServerWebInputException) {
            return write(exchange, HttpStatus.BAD_REQUEST, Problems.body("malformed-request", 400,
                    "The request could not be read: check its body, its parameters and its headers.",
                    Problems.correlationId(exchange)));
        }
        LOG.error("[CM-INTERNAL-ERROR] {} {} ended with an unexpected error (correlationId {})",
                exchange.getRequest().getMethod(), exchange.getRequest().getPath().value(),
                Problems.correlationId(exchange), ex);
        return write(exchange, HttpStatus.INTERNAL_SERVER_ERROR, Problems.body("internal-error", 500,
                "Nothing more is shown here. Quote the correlation id if you report it.", Problems.correlationId(exchange)));
    }

    private Mono<Void> write(ServerWebExchange exchange, HttpStatus status, Map<String, Object> problem) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        return Mono.fromCallable(() -> objectMapper.writeValueAsBytes(problem))
                .map(bytes -> response.bufferFactory().wrap(bytes))
                .flatMap(buffer -> response.writeWith(Mono.just((DataBuffer) buffer)));
    }

    private static boolean isNotOurs(Throwable ex) {
        if (ex instanceof ServerWebInputException) {
            return false;
        }
        String type = ex.getClass().getName();
        return ex instanceof ResponseStatusException
                || type.startsWith("org.springframework.security.")
                || ex instanceof java.util.concurrent.CancellationException
                // a client that went away is not a fault of ours
                || type.endsWith("AbortedException") || type.endsWith("ClientAbortException")
                || (ex.getMessage() != null && ex.getMessage().contains("Connection reset by peer"));
    }
}
