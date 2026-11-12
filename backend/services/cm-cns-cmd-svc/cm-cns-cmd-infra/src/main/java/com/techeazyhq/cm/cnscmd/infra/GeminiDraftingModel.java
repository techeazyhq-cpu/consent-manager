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

package com.techeazyhq.cm.cnscmd.infra;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.techeazyhq.cm.cnscmd.dom.DraftingModelPort;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Mono;

/**
 * {@link DraftingModelPort} against Google's Gemini {@code generateContent} (ADR-113). The key goes in the {@code x-goog-api-key} header, never in
 * the URL, so it cannot reach an access log. The model is asked for JSON.
 */
public class GeminiDraftingModel implements DraftingModelPort {

    private final WebClient client;
    private final String model;
    private final Duration timeout;
    private final int maxTokens;

    public GeminiDraftingModel(WebClient.Builder builder, String baseUrl, String model, String apiKey, Duration timeout, int maxTokens) {
        this.client = builder.baseUrl(baseUrl)
                .codecs(c -> c.defaultCodecs().maxInMemorySize(1024 * 1024))
                .defaultHeader("x-goog-api-key", apiKey == null ? "" : apiKey.strip())
                .build();
        this.model = model;
        this.timeout = timeout;
        this.maxTokens = maxTokens;
    }

    @Override
    public Status status() {
        return new Status(true, model);
    }

    @Override
    public Mono<String> complete(String system, String user) {
        Map<String, Object> body = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", system))),
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", user)))),
                "generationConfig", Map.of("temperature", 0.2, "maxOutputTokens", maxTokens, "responseMimeType", "application/json"));
        return client.post().uri(u -> u.path("/v1beta/models/{model}:generateContent").build(model))
                .contentType(MediaType.APPLICATION_JSON).bodyValue(body)
                .retrieve().bodyToMono(JsonNode.class)
                .timeout(timeout)
                .map(GeminiDraftingModel::text)
                .onErrorMap(e -> !(e instanceof DomRuleViolation), e -> new DomRuleViolation(503, "ai-drafting-unavailable",
                        "The drafting model could not be reached or was too slow: try again shortly"));
    }

    static String text(JsonNode reply) {
        StringBuilder sb = new StringBuilder();
        for (JsonNode part : reply.path("candidates").path(0).path("content").path("parts")) {
            sb.append(part.path("text").asText(""));
        }
        return sb.toString();
    }
}
