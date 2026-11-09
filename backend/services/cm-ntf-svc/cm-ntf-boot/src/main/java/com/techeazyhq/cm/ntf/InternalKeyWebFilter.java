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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import com.techeazyhq.cm.webkit.Problems;

import reactor.core.publisher.Mono;

/**
 * Who may ask this service to send a sign-in code (ADR-083). {@code POST /internal/otp/send} sends an SMS or an email to any
 * address it is given, and was open to anything on the private network. When {@code cm.ntf.internal-api-key} is set, a request
 * must carry the same value in {@code X-Internal-Key}, which Keycloak's sign-in step sends; any other is refused with 401 before
 * it reaches a handler or counts against a limit. Unset, the endpoint is open as before, and a warning at start-up says so.
 *
 * <p>A key is changed without a gap (ADR-101): while {@code cm.ntf.internal-api-key-previous} is also set, a request carrying either is
 * accepted, and each one that carries the previous is counted in {@code cm.ntf.internal.key.previous.used}, so the operator can see
 * when nothing sends it any more and it can be removed.
 */
@Component
public class InternalKeyWebFilter implements WebFilter, Ordered {

    static final String HEADER = "X-Internal-Key";
    static final String PATH = "/internal/otp/send";
    private static final Logger LOG = LoggerFactory.getLogger(InternalKeyWebFilter.class);

    static final String PREVIOUS_USED_METRIC = "cm.ntf.internal.key.previous.used";

    private final byte[] key;
    private final byte[] previous;
    private final MeterRegistry registry;

    public InternalKeyWebFilter(@Value("${cm.ntf.internal-api-key:}") String key,
                                @Value("${cm.ntf.internal-api-key-previous:}") String previous,
                                MeterRegistry registry) {
        this.key = key == null || key.isBlank() ? null : key.getBytes(StandardCharsets.UTF_8);
        this.previous = previous == null || previous.isBlank() ? null : previous.getBytes(StandardCharsets.UTF_8);
        this.registry = registry;
        // exists from the start, at zero, so "nobody uses the previous key" is a number
        registry.counter(PREVIOUS_USED_METRIC);
        if (this.key != null && this.previous != null) {
            LOG.warn("[CM-NTF-INTERNAL-ROTATING] cm.ntf.internal-api-key-previous is set: the old key is still accepted. Remove it once "
                    + "{} stops rising.", PREVIOUS_USED_METRIC);
        }
        if (this.key == null) {
            LOG.warn("[CM-NTF-INTERNAL-OPEN] cm.ntf.internal-api-key is not set: anything that can reach this service can ask it to "
                    + "send sign-in codes. Set it, and the same value in Keycloak's sign-in step, in any deployment.");
        }
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (key == null || !HttpMethod.POST.equals(exchange.getRequest().getMethod())
                || !PATH.equals(exchange.getRequest().getPath().value())) {
            return chain.filter(exchange);
        }
        String given = exchange.getRequest().getHeaders().getFirst(HEADER);
        // compared as digests of equal length so the time taken does not tell how much of a guess was right
        if (given != null) {
            byte[] digest = sha256(given.getBytes(StandardCharsets.UTF_8));
            if (MessageDigest.isEqual(digest, sha256(key))) {
                return chain.filter(exchange);
            }
            if (previous != null && MessageDigest.isEqual(digest, sha256(previous))) {
                registry.counter(PREVIOUS_USED_METRIC).increment();
                return chain.filter(exchange);
            }
        }
        byte[] body = Problems.json("internal-key-invalid", 401, "This request needs the service key", Problems.correlationId(exchange));
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
