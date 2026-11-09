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

import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import io.micrometer.core.instrument.MeterRegistry;
import reactor.core.publisher.Mono;

/**
 * Counts the requests to send a sign-in code that this service accepts ({@code cm.ntf.otp.send.accepted}, ADR-112) and those it refuses (ADR-089): {@code cm.ntf.otp.send.refused} with
 * {@code reason=limited} for a refusal by the send limit (ADR-080, 429) and {@code reason=key} for a request without the service key
 * (ADR-083, 401). A refusal says nothing in a log that anyone reads; a count can be alerted on, and carries no address, number or code.
 * It wraps the key check, so a request refused there is counted too.
 */
@Component
public class OtpSendMetricsWebFilter implements WebFilter, Ordered {

    static final String METRIC = "cm.ntf.otp.send.refused";
    /** Codes this service sent, for the sign-in surge alert (ADR-112): a count, with no address, number or code. */
    static final String ACCEPTED = "cm.ntf.otp.send.accepted";

    private final MeterRegistry registry;

    public OtpSendMetricsWebFilter(MeterRegistry registry) {
        this.registry = registry;
        // both exist from the start, at zero, so "nothing refused" is a number and an alert on a rise has something to rise from
        registry.counter(METRIC, "reason", "limited");
        registry.counter(METRIC, "reason", "key");
        registry.counter(ACCEPTED);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (HttpMethod.POST.equals(exchange.getRequest().getMethod()) && InternalKeyWebFilter.PATH.equals(exchange.getRequest().getPath().value())) {
            exchange.getResponse().beforeCommit(() -> {
                HttpStatusCode status = exchange.getResponse().getStatusCode();
                if (status != null && status.value() == 429) {
                    registry.counter(METRIC, "reason", "limited").increment();
                } else if (status != null && status.value() == 401) {
                    registry.counter(METRIC, "reason", "key").increment();
                } else if (status != null && status.is2xxSuccessful()) {
                    registry.counter(ACCEPTED).increment();
                }
                return Mono.empty();
            });
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 5; // outside the key check, which is +10
    }
}
