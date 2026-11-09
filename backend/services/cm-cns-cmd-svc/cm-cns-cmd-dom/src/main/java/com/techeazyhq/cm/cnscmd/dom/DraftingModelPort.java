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

package com.techeazyhq.cm.cnscmd.dom;

import reactor.core.publisher.Mono;

/**
 * The language model that drafts a purpose from a fiduciary's description (ADR-111): a model the operator runs themselves, reached by
 * an OpenAI-compatible HTTP endpoint (Ollama, vLLM and others speak it). It is given the fiduciary's own words and the platform's rules
 * and nothing else: never a principal's data, a consent or a record.
 */
public interface DraftingModelPort {

    /** Whether drafting is switched on, and with which model; off by default. */
    Status status();

    /**
     * The model's reply to a system message and a user message, as text. Fails with a retryable 503 problem when the model cannot be
     * reached or is too slow.
     */
    Mono<String> complete(String system, String user);

    record Status(boolean enabled, String model) {
    }
}
