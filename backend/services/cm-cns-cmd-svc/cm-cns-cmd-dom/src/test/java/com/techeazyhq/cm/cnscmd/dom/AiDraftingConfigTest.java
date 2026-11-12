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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** What staff may set as the drafting model, and when descriptions would leave the platform (ADR-113). */
class AiDraftingConfigTest {

    private static AiDraftingConfig of(boolean enabled, AiProvider p, String endpoint, String key, boolean ack) {
        return new AiDraftingConfig(enabled, p, endpoint, "some-model", key, 60, 1500, ack, "staff", Instant.EPOCH);
    }

    @Test
    void aVendorNeedsAKeyAndAnAcknowledgementToBeSwitchedOn() {
        assertThrows(IllegalArgumentException.class, () -> of(true, AiProvider.ANTHROPIC, "", null, true).validated());
        assertThrows(IllegalArgumentException.class, () -> of(true, AiProvider.ANTHROPIC, "", "sealed", false).validated());
        of(true, AiProvider.ANTHROPIC, "", "sealed", true).validated();
        of(false, AiProvider.OPENAI, "", null, false).validated(); // off asks for nothing
    }

    @Test
    void aServerOnTheOperatorsOwnNetworkNeedsNeitherKeyNorAcknowledgement() {
        of(true, AiProvider.OLLAMA, "http://localhost:11434", null, false).validated();
        of(true, AiProvider.OPENAI_COMPATIBLE, "http://vllm.models.svc:8000", null, false).validated();
        of(true, AiProvider.OPENAI_COMPATIBLE, "http://10.2.3.4:8000", null, false).validated();
    }

    @Test
    void aServerOutsideTheNetworkLeavesThePlatform() {
        assertTrue(AiDraftingConfig.leavesPlatform(AiProvider.OPENAI_COMPATIBLE, "https://models.example.com"));
        assertThrows(IllegalArgumentException.class, () -> of(true, AiProvider.OPENAI_COMPATIBLE, "https://models.example.com", null, false).validated());
        of(true, AiProvider.OPENAI_COMPATIBLE, "https://models.example.com", null, true).validated();
        assertFalse(AiDraftingConfig.leavesPlatform(AiProvider.OLLAMA, "http://localhost:11434"));
        for (AiProvider p : new AiProvider[] {AiProvider.OPENAI, AiProvider.ANTHROPIC, AiProvider.GEMINI, AiProvider.DEEPSEEK}) {
            assertTrue(AiDraftingConfig.leavesPlatform(p, p.defaultEndpoint()), p.name());
        }
    }

    @Test
    void aVendorsAddressCannotBeChanged() {
        assertThrows(IllegalArgumentException.class, () -> AiDraftingConfig.checkEndpoint(AiProvider.OPENAI, "https://evil.example.com"));
        AiDraftingConfig.checkEndpoint(AiProvider.OPENAI, "https://api.openai.com/");
        AiDraftingConfig.checkEndpoint(AiProvider.OPENAI, "");
        assertEquals("https://api.anthropic.com", of(true, AiProvider.ANTHROPIC, "https://evil.example.com", "k", true).effectiveEndpoint());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "localhost:11434", "ftp://host", "http://user:pw@localhost:1", "http://localhost:1?x=1", "http://localhost:1#f",
            "http://169.254.169.254", "https://169.254.169.254/latest", "http://metadata.google.internal", "http://models.example.com", "not a url"})
    void badAddressesAreRefused(String endpoint) {
        assertThrows(IllegalArgumentException.class, () -> AiDraftingConfig.checkEndpoint(AiProvider.OPENAI_COMPATIBLE, endpoint), endpoint);
    }

    @Test
    void numbersAndNamesAreBounded() {
        assertThrows(IllegalArgumentException.class,
                () -> new AiDraftingConfig(false, AiProvider.OLLAMA, "http://localhost:1", " ", null, 60, 1500, false, "s", null).validated());
        assertThrows(IllegalArgumentException.class,
                () -> new AiDraftingConfig(false, AiProvider.OLLAMA, "http://localhost:1", "m", null, 1, 1500, false, "s", null).validated());
        assertThrows(IllegalArgumentException.class,
                () -> new AiDraftingConfig(false, AiProvider.OLLAMA, "http://localhost:1", "m", null, 60, 10, false, "s", null).validated());
    }
}
