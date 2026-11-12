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

import org.springframework.web.reactive.function.client.WebClient;

import com.techeazyhq.cm.cnscmd.dom.AiDraftingConfig;
import com.techeazyhq.cm.cnscmd.dom.DraftingModelFactoryPort;
import com.techeazyhq.cm.cnscmd.dom.DraftingModelPort;

/** The client for each provider (ADR-113): the OpenAI-shaped ones share one, Anthropic and Gemini have their own. Off builds nothing that can call out. */
public class DraftingModels implements DraftingModelFactoryPort {

    private final WebClient.Builder builder;

    public DraftingModels(WebClient.Builder builder) {
        this.builder = builder;
    }

    @Override
    public DraftingModelPort build(AiDraftingConfig c, String apiKey) {
        if (!c.enabled()) {
            return new NoDraftingModel();
        }
        Duration timeout = Duration.ofSeconds(c.timeoutSeconds());
        String endpoint = c.effectiveEndpoint();
        String model = c.model().strip();
        return switch (c.provider().protocol()) {
            case ANTHROPIC -> new AnthropicDraftingModel(builder.clone(), endpoint, model, apiKey, timeout, c.maxTokens());
            case GEMINI -> new GeminiDraftingModel(builder.clone(), endpoint, model, apiKey, timeout, c.maxTokens());
            case OPENAI_CHAT -> new OpenAiCompatibleDraftingModel(builder.clone(), endpoint, c.provider().chatPath(), model, apiKey, timeout,
                    c.maxTokens());
        };
    }
}
