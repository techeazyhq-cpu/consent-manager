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

package com.techeazyhq.cm.ntf;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import reactor.core.publisher.Mono;

/** The refusals of requests to send a sign-in code are counted, by reason and nothing else (ADR-089). */
class OtpSendMetricsWebFilterTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final OtpSendMetricsWebFilter filter = new OtpSendMetricsWebFilter(registry);

    private double count(String reason) {
        return registry.counter(OtpSendMetricsWebFilter.METRIC, "reason", reason).count();
    }

    private void request(String method, String path, HttpStatus answer) {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.method(org.springframework.http.HttpMethod.valueOf(method), path).build());
        filter.filter(exchange, ex -> {
            ex.getResponse().setStatusCode(answer);
            return ex.getResponse().setComplete();
        }).block();
    }

    @Test
    void bothCountersExistAtZeroFromTheStart() {
        assertEquals(0, count("limited"));
        assertEquals(0, count("key"));
        assertEquals(2, registry.getMeters().size());
    }

    @Test
    void aRefusalByTheLimitAndOneByTheKeyAreCountedApart() {
        request("POST", "/internal/otp/send", HttpStatus.TOO_MANY_REQUESTS);
        request("POST", "/internal/otp/send", HttpStatus.TOO_MANY_REQUESTS);
        request("POST", "/internal/otp/send", HttpStatus.UNAUTHORIZED);

        assertEquals(2, count("limited"));
        assertEquals(1, count("key"));
    }

    @Test
    void aSentCodeAndAnythingElseIsNotCounted() {
        request("POST", "/internal/otp/send", HttpStatus.OK);
        request("POST", "/internal/otp/send", HttpStatus.UNPROCESSABLE_ENTITY);
        request("POST", "/api/ntf/push-devices", HttpStatus.TOO_MANY_REQUESTS);
        request("GET", "/internal/otp/send", HttpStatus.UNAUTHORIZED);

        assertEquals(0, count("limited"));
        assertEquals(0, count("key"));
    }

    @Test
    void noAddressNumberOrCodeIsAnywhereInTheMetric() {
        request("POST", "/internal/otp/send?destination=a@example.com&code=123456", HttpStatus.TOO_MANY_REQUESTS);

        registry.getMeters().forEach(m -> m.getId().getTags().forEach(t -> {
            assertEquals(true, t.getKey().equals("reason") && (t.getValue().equals("limited") || t.getValue().equals("key")));
        }));
    }
}
