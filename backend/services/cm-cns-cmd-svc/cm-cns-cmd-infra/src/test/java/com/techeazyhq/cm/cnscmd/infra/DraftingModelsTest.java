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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import com.techeazyhq.cm.cnscmd.dom.AiDraftingConfig;
import com.techeazyhq.cm.cnscmd.dom.AiProvider;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Mono;

/** Each provider is spoken to the way it expects, with its key in a header and never in the address (ADR-113). */
class DraftingModelsTest {

    /** The services decode replies with Jackson 2's JsonNode, as their Boot configuration sets up; a bare builder does not. */
    private static final ExchangeStrategies STRATEGIES = ExchangeStrategies.builder()
            .codecs(c -> c.customCodecs().register(new org.springframework.http.codec.json.Jackson2JsonDecoder())).build();

    private final AtomicReference<ClientRequest> seen = new AtomicReference<>();

    private DraftingModels models(String body, HttpStatus status) {
        ExchangeFunction fake = request -> {
            seen.set(request);
            return Mono.just(ClientResponse.create(status, STRATEGIES).header("Content-Type", MediaType.APPLICATION_JSON_VALUE).body(body).build());
        };
        return new DraftingModels(WebClient.builder().exchangeFunction(fake));
    }

    private static AiDraftingConfig config(AiProvider p, String endpoint, String model) {
        return new AiDraftingConfig(true, p, endpoint, model, "sealed", 30, 900, true, "staff", Instant.EPOCH);
    }

    @Test
    void ollamaAndOtherOpenAiShapedServersUseTheChatPathOnTheirOwnAddress() {
        String reply = "{\"choices\":[{\"message\":{\"content\":\"{\\\"ok\\\":true}\"}}]}";
        var m = models(reply, HttpStatus.OK).build(config(AiProvider.OLLAMA, "http://ollama.models.svc:11434/", "llama3.2:3b"), null);
        assertEquals("{\"ok\":true}", m.complete("sys", "usr").block());
        assertEquals(URI.create("http://ollama.models.svc:11434/v1/chat/completions"), seen.get().url());
        assertNull(seen.get().headers().getFirst("Authorization"), "no key, no header");
    }

    @Test
    void openAiSendsABearerKeyToItsOwnApiOnly() {
        String reply = "{\"choices\":[{\"message\":{\"content\":\"hi\"}}]}";
        var m = models(reply, HttpStatus.OK).build(config(AiProvider.OPENAI, "https://evil.example.com", "gpt-4o-mini"), "sk-oai");
        assertEquals("hi", m.complete("s", "u").block());
        assertEquals(URI.create("https://api.openai.com/v1/chat/completions"), seen.get().url());
        assertEquals("Bearer sk-oai", seen.get().headers().getFirst("Authorization"));
    }

    @Test
    void deepSeekHasNoV1InItsPath() {
        var m = models("{\"choices\":[{\"message\":{\"content\":\"x\"}}]}", HttpStatus.OK).build(config(AiProvider.DEEPSEEK, "", "deepseek-chat"), "k");
        m.complete("s", "u").block();
        assertEquals(URI.create("https://api.deepseek.com/chat/completions"), seen.get().url());
    }

    @Test
    void anthropicUsesTheMessagesApiWithItsKeyHeader() {
        String reply = "{\"content\":[{\"type\":\"text\",\"text\":\"{\\\"a\\\":\"},{\"type\":\"text\",\"text\":\"1}\"}]}";
        var m = models(reply, HttpStatus.OK).build(config(AiProvider.ANTHROPIC, "", "claude-haiku-5-5"), "sk-ant");
        assertEquals("{\"a\":1}", m.complete("sys", "usr").block());
        assertEquals(URI.create("https://api.anthropic.com/v1/messages"), seen.get().url());
        assertEquals("sk-ant", seen.get().headers().getFirst("x-api-key"));
        assertEquals("2023-06-01", seen.get().headers().getFirst("anthropic-version"));
        assertNull(seen.get().headers().getFirst("Authorization"));
    }

    @Test
    void geminiKeepsItsKeyOutOfTheAddress() {
        String reply = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"{\\\"ok\\\":true}\"}]}}]}";
        var m = models(reply, HttpStatus.OK).build(config(AiProvider.GEMINI, "", "gemini-2.0-flash"), "g-key");
        assertEquals("{\"ok\":true}", m.complete("sys", "usr").block());
        assertEquals(URI.create("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"), seen.get().url());
        assertFalse(seen.get().url().toString().contains("g-key"));
        assertEquals("g-key", seen.get().headers().getFirst("x-goog-api-key"));
    }

    @Test
    void aProviderErrorIsARetryableUnavailableNeverTheProvidersWords() {
        var m = models("{\"error\":{\"message\":\"invalid key sk-leak\"}}", HttpStatus.UNAUTHORIZED)
                .build(config(AiProvider.ANTHROPIC, "", "claude-haiku-5-5"), "sk-leak");
        var e = assertThrows(DomRuleViolation.class, () -> m.complete("s", "u").block());
        assertEquals("ai-drafting-unavailable", e.problemType());
        assertFalse(e.getMessage().contains("sk-leak"));
    }

    @Test
    void offBuildsAClientThatCannotCallOut() {
        var off = new AiDraftingConfig(false, AiProvider.OPENAI, "", "m", "sealed", 30, 900, true, "s", null);
        var m = models("{}", HttpStatus.OK).build(off, "k");
        assertInstanceOf(NoDraftingModel.class, m);
        assertFalse(m.status().enabled());
        assertTrue(seen.get() == null);
    }
}
