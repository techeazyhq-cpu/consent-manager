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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebInputException;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/** What a caller sees (ADR-062): the same shape from every handler, built from the dictionary. */
class ProblemHandlersTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static MockServerWebExchange exchange() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/x").build());
        exchange.getResponse().getHeaders().add(CorrelationIdWebFilter.HEADER, "corr-123");
        return exchange;
    }

    private static Map<String, Object> body(MockServerWebExchange exchange) throws Exception {
        return JSON.readValue(exchange.getResponse().getBodyAsString().block(), new TypeReference<>() {
        });
    }

    @Test
    void aBusinessRuleRejectionCarriesTheDictionarysCodeTitleAndRetryability() throws Exception {
        MockServerWebExchange exchange = exchange();

        new DomProblemDetailWebExceptionHandler(JSON)
                .handle(exchange, new DomRuleViolation(429, "quota-exceeded", "DF d1 is not eligible")).block();

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exchange.getResponse().getStatusCode());
        assertEquals(MediaType.APPLICATION_PROBLEM_JSON, exchange.getResponse().getHeaders().getContentType());
        Map<String, Object> problem = body(exchange);
        assertEquals("urn:cm:problem:quota-exceeded", problem.get("type"));
        assertEquals("quota-exceeded", problem.get("code"));
        assertEquals("Monthly allowance used up", problem.get("title"));
        assertEquals(429, problem.get("status"));
        assertEquals("DF d1 is not eligible", problem.get("detail"));
        assertEquals(true, problem.get("retryable"));
        assertEquals("corr-123", problem.get("correlationId"));
    }

    @Test
    void theTitleIsTheSameWhateverTheDetail() throws Exception {
        MockServerWebExchange first = exchange();
        MockServerWebExchange second = exchange();

        new DomProblemDetailWebExceptionHandler(JSON).handle(first, new DomRuleViolation(404, "webhook-not-found", "No webhook endpoint e1")).block();
        new DomProblemDetailWebExceptionHandler(JSON).handle(second, new DomRuleViolation(404, "webhook-not-found", "No webhook endpoint e2")).block();

        assertEquals(body(first).get("title"), body(second).get("title"));
        assertEquals("Webhook endpoint not found", body(first).get("title"));
        assertEquals(false, body(first).get("retryable"));
    }

    @Test
    void anExceptionThatIsNotARuleViolationIsPassedOn() {
        MockServerWebExchange exchange = exchange();
        IllegalStateException other = new IllegalStateException("not mine");

        Throwable passed = org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> new DomProblemDetailWebExceptionHandler(JSON).handle(exchange, other).block());

        assertEquals(other, passed);
    }

    @Test
    void inputThatCantBeReadIsMalformedRequestWithoutTheParsersWords() throws Exception {
        MockServerWebExchange exchange = exchange();

        new FallbackProblemWebExceptionHandler(JSON)
                .handle(exchange, new ServerWebInputException("JSON parse error: Unexpected character ('}' at line 3)")).block();

        assertEquals(HttpStatus.BAD_REQUEST, exchange.getResponse().getStatusCode());
        Map<String, Object> problem = body(exchange);
        assertEquals("malformed-request", problem.get("code"));
        assertEquals("Request could not be read", problem.get("title"));
        assertFalse(problem.get("detail").toString().contains("Unexpected character"), "nothing of the parser's message");
    }

    @Test
    void anUnexpectedErrorIsInternalErrorWithNothingAboutItInTheAnswer() throws Exception {
        MockServerWebExchange exchange = exchange();

        new FallbackProblemWebExceptionHandler(JSON)
                .handle(exchange, new IllegalStateException("password=hunter2 at jdbc://secret-host")).block();

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exchange.getResponse().getStatusCode());
        String raw = exchange.getResponse().getBodyAsString().block();
        Map<String, Object> problem = JSON.readValue(raw, new TypeReference<>() {
        });
        assertEquals("internal-error", problem.get("code"));
        assertEquals("corr-123", problem.get("correlationId"));
        assertFalse(raw.contains("hunter2") || raw.contains("secret-host"), "the exception is logged, never sent");
        assertEquals(false, problem.get("retryable"));
    }

    @Test
    void whatIsAlreadySomeoneElsesToAnswerIsPassedOn() {
        FallbackProblemWebExceptionHandler handler = new FallbackProblemWebExceptionHandler(JSON);
        for (Throwable ex : new Throwable[] {new ResponseStatusException(HttpStatus.NOT_FOUND),
                new java.util.concurrent.CancellationException("client left")}) {
            MockServerWebExchange exchange = exchange();

            Throwable passed = org.junit.jupiter.api.Assertions.assertThrows(Throwable.class,
                    () -> handler.handle(exchange, ex).block());

            assertEquals(ex, passed);
            assertNull(exchange.getResponse().getStatusCode(), "nothing was written for " + ex.getClass().getSimpleName());
        }
    }

    @Test
    void aResponseAlreadyOnItsWayIsLeftAlone() {
        MockServerWebExchange exchange = exchange();
        ServerHttpResponse response = exchange.getResponse();
        response.setComplete().block();
        assertTrue(response.isCommitted());

        Throwable passed = org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> new FallbackProblemWebExceptionHandler(JSON).handle(exchange, new IllegalStateException("late")).block());

        assertEquals("late", passed.getMessage());
    }

    @Test
    void theCodeIsTheEndOfTheTypeNeverTheTitle() {
        assertEquals("quota-exceeded", Problems.codeOf("urn:cm:problem:quota-exceeded"));
        assertEquals("something-else", Problems.codeOf("something-else"));
        assertNull(Problems.codeOf(null));
    }

    @Test
    void thePlainBodyForFiltersMatchesTheHandlers() throws Exception {
        Map<String, Object> problem = JSON.readValue(Problems.json("rate-limit-exceeded", "Slow down.", null), new TypeReference<>() {
        });

        assertEquals(429, problem.get("status"), "the status comes from the dictionary when the caller doesn't give one");
        assertEquals("Too many requests a second", problem.get("title"));
        assertEquals(true, problem.get("retryable"));
        assertFalse(problem.containsKey("correlationId"));
    }
}
