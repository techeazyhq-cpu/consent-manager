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

package com.techeazyhq.cm.cnscmd.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.techeazyhq.cm.audkit.AuditTrail;
import com.techeazyhq.cm.cnscmd.app.AiDraftingSettings.Update;
import com.techeazyhq.cm.cnscmd.dom.AiDraftingConfig;
import com.techeazyhq.cm.cnscmd.dom.AiDraftingConfigRepoPort;
import com.techeazyhq.cm.cnscmd.dom.AiProvider;
import com.techeazyhq.cm.cnscmd.dom.DraftingModelPort;
import com.techeazyhq.cm.cnscmd.dom.SecretSealerPort;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Mono;

/** The model staff choose in CM Admin is the one that drafts, and its key is never given back or recorded (ADR-113). */
class AiDraftingSettingsTest {

    private final AtomicReference<AiDraftingConfig> stored = new AtomicReference<>();
    private final List<String> built = new ArrayList<>();
    private final List<String> audited = new ArrayList<>();
    private final AtomicReference<Mono<String>> reply = new AtomicReference<>(Mono.just("{\"ok\":true}"));

    private final AiDraftingConfigRepoPort repo = new AiDraftingConfigRepoPort() {
        @Override
        public Mono<AiDraftingConfig> find() {
            return Mono.justOrEmpty(stored.get());
        }

        @Override
        public Mono<AiDraftingConfig> save(AiDraftingConfig c) {
            stored.set(c);
            return Mono.just(c);
        }
    };
    private final SecretSealerPort sealer = new SecretSealerPort() {
        @Override
        public String seal(String secret) {
            return "sealed:" + secret;
        }

        @Override
        public String open(String sealed) {
            return sealed.substring("sealed:".length());
        }
    };
    private final AuditTrail audit = (action, dfId, target, details) -> {
        audited.add(action + " " + target + " " + details);
        return Mono.empty();
    };
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-12T10:00:00Z"), ZoneOffset.UTC);

    private AiDraftingSettings settings(boolean propertiesEnabled) {
        AiDraftingConfig fallback = new AiDraftingConfig(propertiesEnabled, AiProvider.OPENAI_COMPATIBLE, "http://localhost:11434", "llama3.2:3b", null, 100,
                1500, true, "properties", null);
        return new AiDraftingSettings(repo, (c, key) -> {
            built.add(c.provider() + "|" + c.model() + "|key=" + key);
            return c.enabled() ? new DraftingModelPort() {
                @Override
                public Status status() {
                    return new Status(true, c.model());
                }

                @Override
                public Mono<String> complete(String system, String user) {
                    return reply.get();
                }
            } : new DraftingModelPort() {
                @Override
                public Status status() {
                    return new Status(false, null);
                }

                @Override
                public Mono<String> complete(String system, String user) {
                    return Mono.error(new DomRuleViolation(404, "ai-drafting-disabled", "off"));
                }
            };
        }, sealer, audit, clock, fallback, "env-key");
    }

    private static Update anthropic(String key, boolean ack) {
        return new Update(true, AiProvider.ANTHROPIC, null, "claude-haiku-5-5", key, false, 60, 1500, ack);
    }

    @Test
    void thePropertiesApplyUntilStaffSaveASetting() {
        AiDraftingSettings s = settings(true);
        s.reload().block();
        assertTrue(s.status().enabled());
        assertEquals("llama3.2:3b", s.status().model());
        assertTrue(s.view().fromProperties());
        assertTrue(built.get(0).endsWith("key=env-key"));
    }

    @Test
    void aSavedSettingTakesOverAndNeverReturnsTheKey() {
        AiDraftingSettings s = settings(false);
        assertFalse(s.status().enabled());
        var view = s.update("admin-1", anthropic("sk-secret", true)).block();
        assertTrue(s.status().enabled());
        assertEquals("claude-haiku-5-5", s.status().model());
        assertFalse(view.fromProperties());
        assertTrue(view.config().hasKey());
        assertEquals("sealed:sk-secret", stored.get().apiKeySealed());
        assertTrue(built.get(built.size() - 1).endsWith("key=sk-secret"), "the client is built with the key in the clear, in memory only");
        assertTrue(audited.stream().allMatch(a -> !a.contains("sk-secret")), audited.toString());
        assertTrue(audited.get(0).contains("keyChanged=true") && audited.get(0).contains("by=admin-1"), audited.toString());
    }

    @Test
    void aBlankKeyKeepsTheStoredOneWhileTheProviderIsTheSame() {
        AiDraftingSettings s = settings(false);
        s.update("a", anthropic("sk-1", true)).block();
        s.update("a", new Update(true, AiProvider.ANTHROPIC, null, "claude-sonnet-5-5", "", false, 60, 1500, true)).block();
        assertEquals("sealed:sk-1", stored.get().apiKeySealed());
        assertEquals("claude-sonnet-5-5", stored.get().model());
        assertTrue(audited.get(1).contains("keyChanged=false"), audited.toString());
    }

    @Test
    void changingProviderNeverCarriesTheKeyToAnotherVendor() {
        AiDraftingSettings s = settings(false);
        s.update("a", anthropic("sk-anthropic", true)).block();
        // OpenAI needs its own key, so the stored Anthropic key is not reused
        var e = assertThrows(DomRuleViolation.class, () -> s.update("a",
                new Update(true, AiProvider.OPENAI, null, "gpt-4o-mini", "", false, 60, 1500, true)).block());
        assertEquals("invalid-ai-config", e.problemType());
        // and when the key is not required, it is dropped rather than carried over
        s.update("a", new Update(true, AiProvider.OLLAMA, "http://localhost:11434", "llama3.2:3b", "", false, 60, 1500, false)).block();
        assertNull(stored.get().apiKeySealed());
    }

    @Test
    void clearingRemovesTheKey() {
        AiDraftingSettings s = settings(false);
        s.update("a", new Update(true, AiProvider.OPENAI_COMPATIBLE, "http://vllm:8000", "m", "k", false, 60, 1500, false)).block();
        s.update("a", new Update(true, AiProvider.OPENAI_COMPATIBLE, "http://vllm:8000", "m", "", true, 60, 1500, false)).block();
        assertNull(stored.get().apiKeySealed());
    }

    @Test
    void aVendorWithoutAnAcknowledgementIsRefusedAndNothingIsSaved() {
        AiDraftingSettings s = settings(false);
        var e = assertThrows(DomRuleViolation.class, () -> s.update("a", anthropic("k", false)).block());
        assertEquals("invalid-ai-config", e.problemType());
        assertNull(stored.get());
        assertFalse(s.status().enabled());
        assertTrue(audited.isEmpty());
    }

    @Test
    void anotherInstancePicksUpAChangeOnReload() {
        AiDraftingSettings a = settings(false);
        AiDraftingSettings b = settings(false);
        a.update("a", anthropic("k", true)).block();
        assertFalse(b.status().enabled());
        b.reload().block();
        assertTrue(b.status().enabled());
        assertEquals("claude-haiku-5-5", b.status().model());
    }

    @Test
    void aFailedReloadKeepsTheClientInUse() {
        AiDraftingSettings s = settings(true);
        AiDraftingConfigRepoPort broken = new AiDraftingConfigRepoPort() {
            @Override
            public Mono<AiDraftingConfig> find() {
                return Mono.error(new IllegalStateException("db down"));
            }

            @Override
            public Mono<AiDraftingConfig> save(AiDraftingConfig c) {
                return Mono.just(c);
            }
        };
        AiDraftingSettings s2 = new AiDraftingSettings(broken, (c, k) -> s.status().enabled() ? new DraftingModelPort() {
            @Override
            public Status status() {
                return new Status(true, "kept");
            }

            @Override
            public Mono<String> complete(String system, String user) {
                return Mono.just("{}");
            }
        } : null, sealer, audit, clock, new AiDraftingConfig(true, AiProvider.OLLAMA, "http://localhost:1", "m", null, 60, 1500, true, "p", null), null);
        s2.reload().block();
        assertEquals("kept", s2.status().model());
    }

    @Test
    void theConnectionTestReportsSuccessWithoutSaving() {
        AiDraftingSettings s = settings(false);
        var r = s.test("a", new Update(false, AiProvider.ANTHROPIC, null, "claude-haiku-5-5", "sk-try", false, 60, 1500, false)).block();
        assertTrue(r.ok());
        assertNull(stored.get(), "a test saves nothing");
        assertFalse(s.status().enabled(), "and changes nothing in use");
        assertTrue(built.get(built.size() - 1).endsWith("key=sk-try"));
        assertTrue(audited.get(0).contains("outcome=ok") && !audited.get(0).contains("sk-try"), audited.toString());
    }

    @Test
    void aFailedTestSaysSoWithoutEchoingWhatTheProviderSaid() {
        AiDraftingSettings s = settings(false);
        reply.set(Mono.error(new IllegalStateException("401 invalid x-api-key: sk-try")));
        var r = s.test("a", anthropic("sk-try", true)).block();
        assertFalse(r.ok());
        assertFalse(r.message().contains("sk-try"));
        assertTrue(audited.get(0).contains("outcome=failed"), audited.toString());
    }

    @Test
    void aTestOfABadSettingIsRefused() {
        AiDraftingSettings s = settings(false);
        var e = assertThrows(DomRuleViolation.class, () -> s.test("a",
                new Update(true, AiProvider.OPENAI_COMPATIBLE, "http://169.254.169.254", "m", "", false, 60, 1500, true)).block());
        assertEquals("invalid-ai-config", e.problemType());
    }

    @Test
    void everyProviderIsListedWithWhatItNeeds() {
        var list = AiDraftingSettings.providers();
        assertEquals(AiProvider.values().length, list.size());
        assertTrue(list.stream().anyMatch(p -> p.id() == AiProvider.GEMINI && p.keyRequired() && p.thirdParty() && !p.endpointEditable()));
        assertTrue(list.stream().anyMatch(p -> p.id() == AiProvider.OLLAMA && !p.keyRequired() && !p.thirdParty() && p.endpointEditable()));
    }
}
