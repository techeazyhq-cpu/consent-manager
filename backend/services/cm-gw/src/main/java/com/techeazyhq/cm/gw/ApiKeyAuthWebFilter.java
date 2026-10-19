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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * API-key and signed requests (ADR-041). A request with {@code X-API-Key} is checked before Spring Security sees it:
 * the gateway hashes the exact body bytes and asks cm-df-reg-svc, which holds the keys, signing secrets and nonces,
 * to verify the key, the signature, its time window and its single-use nonce. On success the key and signature headers
 * are replaced by {@code Authorization: Bearer} with the fiduciary's API client token, so everything after this point,
 * the security chain, quotas, the PEP and the services, sees the same request as with OAuth client credentials. On
 * failure the request ends here with the reason, as {@code application/problem+json}.
 *
 * <p>Fails closed: if cm-df-reg-svc can't be reached, an API-key request is refused (503), never let through.
 */
@Component
public class ApiKeyAuthWebFilter implements WebFilter, Ordered {

    static final String API_KEY = "X-API-Key";
    static final String SIGNATURE = "X-Signature";
    static final String TIMESTAMP = "X-Signature-Timestamp";
    static final String NONCE = "X-Signature-Nonce";
    /** The largest body hashed: a 5 MB batch file in a multipart upload, with room to spare. */
    static final int MAX_BODY = 12 * 1024 * 1024;

    private static final HexFormat HEX = HexFormat.of();
    private static final ObjectMapper JSON = new ObjectMapper();

    private final WebClient dfReg;
    private final ReactiveCircuitBreaker breaker;

    public ApiKeyAuthWebFilter(WebClient.Builder builder, ReactiveCircuitBreakerFactory<?, ?> cbFactory,
                               @Value("${cm.service-urls.df-reg}") String dfRegBaseUrl) {
        this.dfReg = builder.baseUrl(dfRegBaseUrl).build();
        this.breaker = cbFactory.create("df-reg-api-keys");
    }

    /** Before the security chain (order -100), which would otherwise refuse a request without a bearer token. */
    @Override
    public int getOrder() {
        return -200;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String apiKey = request.getHeaders().getFirst(API_KEY);
        if (apiKey == null || !request.getURI().getRawPath().startsWith("/api/")) {
            return chain.filter(exchange);
        }
        if (request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
            return problem(exchange, HttpStatus.BAD_REQUEST, "ambiguous-credentials",
                    "Send either X-API-Key or Authorization, not both");
        }
        return DataBufferUtils.join(request.getBody(), MAX_BODY)
                .map(buffer -> {
                    byte[] bytes = new byte[buffer.readableByteCount()];
                    buffer.read(bytes);
                    DataBufferUtils.release(buffer);
                    return bytes;
                })
                .defaultIfEmpty(new byte[0])
                .flatMap(body -> verify(request, apiKey, body)
                        .flatMap(token -> chain.filter(exchange.mutate()
                                .request(withToken(exchange, request, token, body)).build()))
                        .onErrorResume(Refused.class, r -> problem(exchange, r.status, r.type, r.detail)))
                .onErrorResume(DataBufferLimitException.class, e -> problem(exchange, HttpStatus.PAYLOAD_TOO_LARGE,
                        "payload-too-large", "A signed request's body is at most " + MAX_BODY / (1024 * 1024) + " MB"));
    }

    private Mono<String> verify(ServerHttpRequest request, String apiKey, byte[] body) {
        HttpHeaders h = request.getHeaders();
        Map<String, Object> check = new LinkedHashMap<>();
        check.put("apiKey", apiKey);
        check.put("method", request.getMethod().name());
        check.put("path", request.getURI().getRawPath());
        check.put("query", request.getURI().getRawQuery());
        check.put("bodySha256", sha256Hex(body));
        check.put("timestamp", h.getFirst(TIMESTAMP));
        check.put("nonce", h.getFirst(NONCE));
        check.put("signature", h.getFirst(SIGNATURE));
        Mono<String> call = dfReg.post().uri("/internal/api-keys/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(check)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(v -> v.path("accessToken").asText())
                .onErrorMap(WebClientResponseException.class, e -> e.getStatusCode().is4xxClientError()
                        ? Refused.from(e) : e);
        return breaker.run(call, err -> err instanceof Refused ? Mono.error(err)
                : Mono.error(new Refused(HttpStatus.SERVICE_UNAVAILABLE, "api-key-check-unavailable",
                        "API keys can't be checked right now; try again shortly, or use an OAuth access token")));
    }

    /** The request as it goes on: a bearer token instead of the key and signature, and the body it came with. */
    private static ServerHttpRequest withToken(ServerWebExchange exchange, ServerHttpRequest request, String token,
                                               byte[] body) {
        ServerHttpRequest mutated = request.mutate().headers(headers -> {
            headers.remove(API_KEY);
            headers.remove(SIGNATURE);
            headers.remove(TIMESTAMP);
            headers.remove(NONCE);
            headers.setBearerAuth(token);
        }).build();
        return new ServerHttpRequestDecorator(mutated) {
            @Override
            public Flux<DataBuffer> getBody() {
                return body.length == 0 ? Flux.empty()
                        : Flux.defer(() -> Flux.just(exchange.getResponse().bufferFactory().wrap(body)));
            }
        };
    }

    private static Mono<Void> problem(ServerWebExchange exchange, HttpStatus status, String type, String detail) {
        var response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "urn:cm:problem:" + type);
        body.put("title", type);
        body.put("status", status.value());
        body.put("detail", detail);
        byte[] bytes;
        try {
            bytes = JSON.writeValueAsBytes(body);
        } catch (Exception e) {
            bytes = ("{\"type\":\"urn:cm:problem:" + type + "\"}").getBytes(StandardCharsets.UTF_8);
        }
        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    }

    static String sha256Hex(byte[] bytes) {
        try {
            return HEX.formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    /** A refusal from cm-df-reg-svc, passed on to the caller as it was given. */
    static final class Refused extends RuntimeException {

        final HttpStatus status;
        final String type;
        final String detail;

        Refused(HttpStatus status, String type, String detail) {
            super(type, null, false, false);
            this.status = status;
            this.type = type;
            this.detail = detail;
        }

        static Refused from(WebClientResponseException e) {
            HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
            try {
                JsonNode p = JSON.readTree(e.getResponseBodyAsByteArray());
                String type = p.path("title").asText(p.path("type").asText("api-key-invalid").replace("urn:cm:problem:", ""));
                return new Refused(status == null ? HttpStatus.UNAUTHORIZED : status, type, p.path("detail").asText(type));
            } catch (Exception parse) {
                return new Refused(status == null ? HttpStatus.UNAUTHORIZED : status, "api-key-invalid",
                        "The API key or signature was refused");
            }
        }
    }
}
