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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import reactor.core.publisher.Mono;

/** Who may ask for a sign-in code to be sent (ADR-083). */
class InternalKeyWebFilterTest {

    private final AtomicInteger reached = new AtomicInteger();

    private HttpStatus post(InternalKeyWebFilter filter, String path, String key) {
        MockServerHttpRequest.BodyBuilder b = MockServerHttpRequest.post(path);
        if (key != null) {
            b.header("X-Internal-Key", key);
        }
        MockServerWebExchange exchange = MockServerWebExchange.from(b.build());
        filter.filter(exchange, ex -> {
            reached.incrementAndGet();
            return Mono.empty();
        }).block();
        return (HttpStatus) exchange.getResponse().getStatusCode();
    }

    @Test
    void withAKeySetOnlyARequestCarryingItReachesTheHandler() {
        InternalKeyWebFilter filter = new InternalKeyWebFilter("s3cret-value");

        assertNull(post(filter, "/internal/otp/send", "s3cret-value"));
        assertEquals(1, reached.get());
        assertEquals(HttpStatus.UNAUTHORIZED, post(filter, "/internal/otp/send", "wrong"));
        assertEquals(HttpStatus.UNAUTHORIZED, post(filter, "/internal/otp/send", null));
        assertEquals(HttpStatus.UNAUTHORIZED, post(filter, "/internal/otp/send", "s3cret-valu"));
        assertEquals(HttpStatus.UNAUTHORIZED, post(filter, "/internal/otp/send", "s3cret-value-and-more"));
        assertEquals(1, reached.get(), "a refused request never reached the handler");
    }

    @Test
    void theRefusalIsAProblemThatGivesNothingAway() {
        InternalKeyWebFilter filter = new InternalKeyWebFilter("s3cret-value");
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/internal/otp/send").build());

        filter.filter(exchange, ex -> Mono.empty()).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        String body = exchange.getResponse().getBodyAsString().block();
        assertTrue(body.contains("internal-key-invalid"));
        assertTrue(!body.contains("s3cret"));
    }

    @Test
    void otherPathsAndMethodsAreNotItsBusiness() {
        InternalKeyWebFilter filter = new InternalKeyWebFilter("s3cret-value");

        assertNull(post(filter, "/internal/other", null));
        assertNull(post(filter, "/api/ntf/push-devices", null));
        MockServerWebExchange get = MockServerWebExchange.from(MockServerHttpRequest.get("/internal/otp/send").build());
        filter.filter(get, ex -> Mono.empty()).block();
        assertNull(get.getResponse().getStatusCode());
    }

    @Test
    void withNoKeySetTheEndpointIsOpenAsBefore() {
        assertNull(post(new InternalKeyWebFilter(""), "/internal/otp/send", null));
        assertNull(post(new InternalKeyWebFilter(null), "/internal/otp/send", null));
    }
}
