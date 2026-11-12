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

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.techeazyhq.cm.cnscmd.dom.DraftingModelPort;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Mono;

/**
 * {@link DraftingModelPort} against an OpenAI-compatible chat endpoint the operator runs (ADR-111): Ollama, vLLM, llama.cpp's server and others
 * speak {@code POST /v1/chat/completions}. Nothing leaves the operator's own infrastructure unless they point it elsewhere. The model is asked
 * for JSON, a low temperature, a bounded reply, and given a time to answer; a model that cannot be reached or is too slow is a retryable 503.
 */
public class OpenAiCompatibleDraftingModel implements DraftingModelPort {

    private final WebClient client;
    private final String model;
    private final String chatPath;
    private final Duration timeout;
    private final int maxTokens;

    /** {@code apiKey} is optional: a self-hosted endpoint often has none. It is sent as a bearer token and never logged. */
    public OpenAiCompatibleDraftingModel(WebClient.Builder builder, String baseUrl, String model, String apiKey, Duration timeout, int maxTokens) {
        this(builder, baseUrl, "/v1/chat/completions", model, apiKey, timeout, maxTokens);
    }

    /** {@code chatPath} is where the provider takes a chat call: {@code /v1/chat/completions}, or {@code /chat/completions} for DeepSeek. */
    public OpenAiCompatibleDraftingModel(WebClient.Builder builder, String baseUrl, String chatPath, String model, String apiKey, Duration timeout,
                                         int maxTokens) {
        WebClient.Builder b = builder.baseUrl(baseUrl)
                .codecs(c -> c.defaultCodecs().maxInMemorySize(1024 * 1024));
        if (apiKey != null && !apiKey.isBlank()) {
            b = b.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey.strip());
        }
        this.client = b.build();
        this.model = model;
        this.chatPath = chatPath;
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
                "messages", List.of(Map.of("role", "system", "content", system), Map.of("role", "user", "content", user)),
                "temperature", 0.2,
                "max_tokens", maxTokens,
                "stream", false,
                "response_format", Map.of("type", "json_object"));
        return client.post().uri(chatPath).contentType(MediaType.APPLICATION_JSON).bodyValue(body)
                .retrieve().bodyToMono(JsonNode.class)
                .timeout(timeout)
                .map(n -> n.path("choices").path(0).path("message").path("content").asText(""))
                .onErrorMap(e -> !(e instanceof DomRuleViolation), e -> new DomRuleViolation(503, "ai-drafting-unavailable",
                        "The drafting model could not be reached or was too slow: try again shortly"));
    }
}
