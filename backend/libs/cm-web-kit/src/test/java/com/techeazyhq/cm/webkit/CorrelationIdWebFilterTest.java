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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

/** One correlation id per call, the same in the gateway and the service, and never a string a caller made up. */
class CorrelationIdWebFilterTest {

    private final CorrelationIdWebFilter filter = new CorrelationIdWebFilter();

    private static MockServerWebExchange exchange(String... correlationIds) {
        MockServerHttpRequest.BaseBuilder<?> request = MockServerHttpRequest.get("/api/x");
        for (String id : correlationIds) {
            request.header(CorrelationIdWebFilter.HEADER, id);
        }
        return MockServerWebExchange.from(request.build());
    }

    /** Runs the filter around a handler that answers, as a service behind the gateway would, with a copy of its own. */
    private ServerWebExchange run(MockServerWebExchange exchange, AtomicReference<ServerWebExchange> seenDownstream, String downstreamAnswerId) {
        WebFilterChain chain = next -> {
            seenDownstream.set(next);
            if (downstreamAnswerId != null) {
                next.getResponse().getHeaders().add(CorrelationIdWebFilter.HEADER, downstreamAnswerId);
            }
            return next.getResponse().setComplete();
        };
        filter.filter(exchange, chain).block();
        return exchange;
    }

    private static List<String> responseIds(ServerWebExchange exchange) {
        return exchange.getResponse().getHeaders().getOrDefault(CorrelationIdWebFilter.HEADER, List.of());
    }

    @Test
    void anIdAlreadyInTheRequestIsKeptAndSentBack() {
        AtomicReference<ServerWebExchange> downstream = new AtomicReference<>();

        MockServerWebExchange exchange = (MockServerWebExchange) run(exchange("gw-123"), downstream, null);

        assertEquals(List.of("gw-123"), responseIds(exchange));
        assertEquals("gw-123", downstream.get().getRequest().getHeaders().getFirst(CorrelationIdWebFilter.HEADER));
    }

    @Test
    void withNoneOneIsMadeAndPassedOnSoWhatTheCallCallsNextSharesIt() {
        AtomicReference<ServerWebExchange> downstream = new AtomicReference<>();

        MockServerWebExchange exchange = (MockServerWebExchange) run(exchange(), downstream, null);

        String id = responseIds(exchange).get(0);
        assertEquals(1, responseIds(exchange).size());
        assertFalse(id.isBlank());
        assertEquals(id, downstream.get().getRequest().getHeaders().getFirst(CorrelationIdWebFilter.HEADER),
                "the gateway's proxied request carries the id the gateway sent back");
    }

    @Test
    void aServiceBehindTheGatewayCantMakeTheResponseCarryTwo() {
        AtomicReference<ServerWebExchange> downstream = new AtomicReference<>();

        // the gateway's response also has the copy the service answered with: the caller gets one value, the call's own
        MockServerWebExchange exchange = (MockServerWebExchange) run(exchange(), downstream, "another-id-from-the-service");

        assertEquals(1, responseIds(exchange).size(), responseIds(exchange).toString());
        assertEquals(downstream.get().getRequest().getHeaders().getFirst(CorrelationIdWebFilter.HEADER), responseIds(exchange).get(0));
    }

    @Test
    void anIdThatIsNotAPlainTokenIsReplacedNotReflected() {
        for (String hostile : new String[] {"a\r\nSet-Cookie: x=1", "x".repeat(65), "has space", "<script>alert(1)</script>",
                "{\"a\":1}", "ünïcode"}) {
            AtomicReference<ServerWebExchange> downstream = new AtomicReference<>();

            MockServerWebExchange exchange = (MockServerWebExchange) run(exchange(hostile), downstream, null);

            List<String> ids = responseIds(exchange);
            assertEquals(1, ids.size());
            assertFalse(ids.get(0).equals(hostile), "not reflected: " + hostile);
            assertTrue(ids.get(0).matches("[A-Za-z0-9._:-]{1,64}"), ids.get(0));
            assertEquals(ids.get(0), downstream.get().getRequest().getHeaders().getFirst(CorrelationIdWebFilter.HEADER));
        }
    }

    @Test
    void twoIdsInTheRequestBecomeOne() {
        AtomicReference<ServerWebExchange> downstream = new AtomicReference<>();

        MockServerWebExchange exchange = (MockServerWebExchange) run(exchange("first-id", "second-id"), downstream, null);

        assertEquals(List.of("first-id"), responseIds(exchange));
        assertEquals(List.of("first-id"), downstream.get().getRequest().getHeaders().get(CorrelationIdWebFilter.HEADER));
    }

    @Test
    void theIdIsInTheReactorContextForDownstreamCode() {
        AtomicReference<Object> inContext = new AtomicReference<>();
        WebFilterChain chain = next -> Mono.deferContextual(ctx -> {
            inContext.set(ctx.getOrDefault(CorrelationIdWebFilter.CONTEXT_KEY, null));
            return Mono.empty();
        });

        filter.filter(exchange("ctx-1"), chain).block();

        assertEquals("ctx-1", inContext.get());
    }
}
