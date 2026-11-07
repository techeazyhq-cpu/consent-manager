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

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The one place an API error response is built (ADR-062), so every service and the gateway answer the same shape
 * ({@code application/problem+json}, RFC 9457) from the {@link ErrorDictionary}:
 * <pre>{"type":"urn:cm:problem:quota-exceeded","code":"quota-exceeded","title":"Monthly allowance used up",
 *  "status":429,"detail":"...","retryable":true,"correlationId":"..."}</pre>
 * {@code code} (also the end of {@code type}) is the stable name to write logic against; {@code title} is the dictionary's
 * words for it and never changes between occurrences; {@code detail} says what was wrong this time. The URN is written
 * here and nowhere else: CI refuses a problem built by hand.
 */
public final class Problems {

    public static final String TYPE_PREFIX = "urn:cm:problem:";
    private static final ObjectMapper JSON = new ObjectMapper();

    private Problems() {
    }

    /** The problem for {@code code}, with the status the dictionary gives it. {@code detail} and {@code correlationId} may be null. */
    public static Map<String, Object> body(String code, String detail, String correlationId) {
        return body(code, ErrorDictionary.instance().status(code, 500), detail, correlationId);
    }

    /**
     * As above with the status the caller has. A problem raised with a status other than the dictionary's is a mistake
     * the CI check catches in the source; here the caller's status is what is sent, so a request is never answered with
     * a different one than its handler meant.
     */
    public static Map<String, Object> body(String code, int status, String detail, String correlationId) {
        ErrorDictionary dictionary = ErrorDictionary.instance();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", TYPE_PREFIX + code);
        body.put("code", code);
        body.put("title", dictionary.title(code));
        body.put("status", status);
        if (detail != null && !detail.isBlank()) {
            body.put("detail", detail);
        }
        body.put("retryable", dictionary.retryable(code, status));
        if (correlationId != null && !correlationId.isBlank()) {
            body.put("correlationId", correlationId);
        }
        return body;
    }

    /** The problem as JSON bytes, for filters that write the response themselves. */
    public static byte[] json(String code, String detail, String correlationId) {
        return bytes(body(code, detail, correlationId));
    }

    public static byte[] json(String code, int status, String detail, String correlationId) {
        return bytes(body(code, status, detail, correlationId));
    }

    static byte[] bytes(Map<String, Object> body) {
        try {
            return JSON.writeValueAsBytes(body);
        } catch (JsonProcessingException e) {
            // a map of strings, numbers and booleans always serialises; this keeps the answer a problem if it ever doesn't
            return ("{\"type\":\"" + body.get("type") + "\",\"status\":" + body.get("status") + "}")
                    .getBytes(StandardCharsets.UTF_8);
        }
    }

    /** The call's correlation id: the one echoed on the response, else the one it came in with. */
    public static String correlationId(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        String id = response.getHeaders().getFirst(CorrelationIdWebFilter.HEADER);
        if (id == null || id.isBlank()) {
            ServerHttpRequest request = exchange.getRequest();
            id = request.getHeaders().getFirst(CorrelationIdWebFilter.HEADER);
        }
        return id;
    }

    /**
     * The problem a response carries. The code is the end of its {@code type}, never its {@code title}: a title is words
     * for people and the dictionary may improve them.
     */
    public static String codeOf(String type) {
        return type != null && type.startsWith(TYPE_PREFIX) ? type.substring(TYPE_PREFIX.length()) : type;
    }
}
