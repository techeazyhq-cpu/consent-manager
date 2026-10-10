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

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * The gateway acting as the Policy Enforcement Point (plan §9, Stage 3 Dynamic Enforcement):
 * intercepts {@code GET /api/cns-qry/cns-arts/{id}/verify}, extracts the caller-presented
 * {@code CnsTkn} header, delegates the allow/deny decision to {@code cm-pdp} (OPA), and only
 * forwards the request downstream on {@code allow: true} — otherwise short-circuits with 403. The caller's own
 * fiduciary id goes to the policy too, which only honours a token for the fiduciary it names (ADR-006).
 */
@Component
public class PepVerifyGatewayFilter implements GlobalFilter, Ordered {

    private static final Pattern VERIFY_PATH = Pattern.compile("^/api/cns-qry/cns-arts/([^/]+)/verify$");
    private static final String CNS_TKN_HEADER = "X-Cns-Tkn";

    private final WebClient opaWebClient;

    public PepVerifyGatewayFilter(WebClient.Builder builder, @Value("${cm.pdp.base-url}") String pdpBaseUrl) {
        this.opaWebClient = builder.baseUrl(pdpBaseUrl).build();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        Matcher matcher = VERIFY_PATH.matcher(path);
        if (!matcher.matches()) {
            return chain.filter(exchange);
        }

        String cnsArtId = matcher.group(1);
        String cnsTkn = exchange.getRequest().getHeaders().getFirst(CNS_TKN_HEADER);
        String purpose = exchange.getRequest().getQueryParams().getFirst("purpose");
        String attribute = exchange.getRequest().getQueryParams().getFirst("attribute");

        if (cnsTkn == null) {
            return deny(exchange);
        }

        return callerDfId(exchange)
                .flatMap(dfId -> opaWebClient.post()
                        .uri("/v1/data/consent/authorization/allow")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Map.of("input", Map.of(
                                "action", "READ",
                                "cns_art_id", cnsArtId,
                                "consent_token", cnsTkn,
                                "caller_df_id", dfId,
                                "requested_purpose", purpose == null ? "" : purpose,
                                "requested_attribute", attribute == null ? "" : attribute)))
                        .retrieve()
                        .bodyToMono(OpaResp.class))
                .flatMap(resp -> Boolean.TRUE.equals(resp.result()) ? chain.filter(exchange) : deny(exchange))
                .onErrorResume(err -> deny(exchange)); // fail closed: PDP unreachable -> deny
    }

    /**
     * The fiduciary making the call, from its own access token ({@code df_id}, ADR-008). A token is only honoured for
     * the fiduciary it was issued to, so a caller without a fiduciary identity gets an empty id and is denied.
     */
    private static Mono<String> callerDfId(ServerWebExchange exchange) {
        return exchange.getPrincipal()
                .filter(JwtAuthenticationToken.class::isInstance)
                .map(principal -> ((JwtAuthenticationToken) principal).getToken().getClaimAsString("df_id"))
                .defaultIfEmpty("");
    }

    private Mono<Void> deny(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        byte[] body = "{\"type\":\"urn:cm:problem:consent-not-verified\",\"title\":\"DENY\"}".getBytes();
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private record OpaResp(Boolean result) {
    }
}
