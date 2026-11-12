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

/**
 * The model providers the platform's operator can choose for drafting purposes (ADR-113). Each knows how it is spoken to ({@link Protocol}),
 * where it lives by default, and whether the operator may point it elsewhere. A provider whose endpoint is fixed can never be pointed at an
 * address of the operator's choosing, so a typo or a hostile edit cannot send fiduciaries' words anywhere but the vendor named.
 */
public enum AiProvider {

    /** A model run on the operator's own hardware (Ollama). No key. */
    OLLAMA("Ollama", Protocol.OPENAI_CHAT, "http://localhost:11434", "llama3.2:3b", true, false),
    /** Any other server that speaks {@code /v1/chat/completions}: vLLM, llama.cpp, LiteLLM, Azure OpenAI's compatible route. */
    OPENAI_COMPATIBLE("OpenAI-compatible server", Protocol.OPENAI_CHAT, "http://localhost:8000", "", true, false),
    OPENAI("OpenAI", Protocol.OPENAI_CHAT, "https://api.openai.com", "gpt-4o-mini", false, true),
    ANTHROPIC("Anthropic", Protocol.ANTHROPIC, "https://api.anthropic.com", "claude-haiku-5-5", false, true),
    GEMINI("Google Gemini", Protocol.GEMINI, "https://generativelanguage.googleapis.com", "gemini-2.0-flash", false, true),
    DEEPSEEK("DeepSeek", Protocol.OPENAI_CHAT, "https://api.deepseek.com", "deepseek-chat", false, true);

    /** How a provider is spoken to. */
    public enum Protocol {
        /** {@code POST {endpoint}{chatPath}} with an OpenAI chat body. */
        OPENAI_CHAT,
        /** Anthropic's Messages API. */
        ANTHROPIC,
        /** Google's {@code generateContent}. */
        GEMINI
    }

    private final String label;
    private final Protocol protocol;
    private final String defaultEndpoint;
    private final String defaultModel;
    private final boolean endpointEditable;
    private final boolean keyRequired;

    AiProvider(String label, Protocol protocol, String defaultEndpoint, String defaultModel, boolean endpointEditable, boolean keyRequired) {
        this.label = label;
        this.protocol = protocol;
        this.defaultEndpoint = defaultEndpoint;
        this.defaultModel = defaultModel;
        this.endpointEditable = endpointEditable;
        this.keyRequired = keyRequired;
    }

    public String label() {
        return label;
    }

    public Protocol protocol() {
        return protocol;
    }

    public String defaultEndpoint() {
        return defaultEndpoint;
    }

    public String defaultModel() {
        return defaultModel;
    }

    /** Whether the operator sets the address (a server they run); false for a vendor's own API. */
    public boolean endpointEditable() {
        return endpointEditable;
    }

    /** Whether a vendor's API refuses calls without a key. A self-run server may or may not want one. */
    public boolean keyRequired() {
        return keyRequired;
    }

    /** The path of the chat call for an OpenAI-shaped provider; DeepSeek has no {@code /v1}. */
    public String chatPath() {
        return this == DEEPSEEK ? "/chat/completions" : "/v1/chat/completions";
    }
}
