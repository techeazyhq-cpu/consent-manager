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
 * {@link DraftingModelPort} against Anthropic's Messages API (ADR-113). The key goes in {@code x-api-key} and is never logged. The reply is the
 * text blocks of the answer joined; the caller asks for one JSON object and checks what it gets, as for every provider.
 */
public class AnthropicDraftingModel implements DraftingModelPort {

    static final String VERSION = "2023-06-01";

    private final WebClient client;
    private final String model;
    private final Duration timeout;
    private final int maxTokens;

    public AnthropicDraftingModel(WebClient.Builder builder, String baseUrl, String model, String apiKey, Duration timeout, int maxTokens) {
        this.client = builder.baseUrl(baseUrl)
                .codecs(c -> c.defaultCodecs().maxInMemorySize(1024 * 1024))
                .defaultHeader("x-api-key", apiKey == null ? "" : apiKey.strip())
                .defaultHeader("anthropic-version", VERSION)
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
                "model", model,
                "max_tokens", maxTokens,
                "temperature", 0.2,
                "system", system,
                "messages", List.of(Map.of("role", "user", "content", user)));
        return client.post().uri("/v1/messages").contentType(MediaType.APPLICATION_JSON).bodyValue(body)
                .retrieve().bodyToMono(JsonNode.class)
                .timeout(timeout)
                .map(AnthropicDraftingModel::text)
                .onErrorMap(e -> !(e instanceof DomRuleViolation), e -> new DomRuleViolation(503, "ai-drafting-unavailable",
                        "The drafting model could not be reached or was too slow: try again shortly"));
    }

    static String text(JsonNode reply) {
        StringBuilder sb = new StringBuilder();
        for (JsonNode block : reply.path("content")) {
            if ("text".equals(block.path("type").asText())) {
                sb.append(block.path("text").asText(""));
            }
        }
        return sb.toString();
    }
}
