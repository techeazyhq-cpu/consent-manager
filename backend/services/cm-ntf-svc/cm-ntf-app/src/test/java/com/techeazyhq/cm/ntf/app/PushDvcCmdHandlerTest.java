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
package com.techeazyhq.cm.ntf.app;

import com.techeazyhq.cm.ntf.dom.PushDvc;
import com.techeazyhq.cm.ntf.dom.PushDvcRepoPort;
import com.techeazyhq.cm.ntf.dom.PushPltf;
import com.techeazyhq.cm.webkit.DomRuleViolation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PushDvcCmdHandlerTest {

    private final List<PushPltf> stored = new ArrayList<>();
    private final PushDvcCmdHandler handler = new PushDvcCmdHandler(new RecordingRepo());

    @ParameterizedTest
    @EnumSource(PushPltf.class)
    void aBrowserOrEitherPhoneCanRegister(PushPltf platform) {
        PushDvc dvc = handler.register("dp-1", "fcm-token", platform).block();

        assertEquals(platform, dvc.platform());
        assertEquals(List.of(platform), stored);
    }

    @Test
    void aRegistrationNamingNoPlatformIsABrowser() {
        assertEquals(PushPltf.WEB, handler.register("dp-1", "fcm-token", null).block().platform());
    }

    @Test
    void aMissingOrOversizedTokenIsRefused() {
        for (String token : new String[] {null, " ", "x".repeat(PushDvc.MAX_TOKEN_LENGTH + 1)}) {
            DomRuleViolation err = assertThrows(DomRuleViolation.class,
                    () -> handler.register("dp-1", token, PushPltf.ANDROID).block());
            assertEquals("push-token-invalid", err.problemType());
        }
        assertTrue(stored.isEmpty());
    }

    private final class RecordingRepo implements PushDvcRepoPort {
        @Override
        public Mono<PushDvc> upsert(String dpId, String token, PushPltf platform) {
            stored.add(platform);
            return Mono.just(new PushDvc("d1", dpId, token, platform, Instant.now(), Instant.now()));
        }

        @Override
        public Flux<PushDvc> findByDpId(String dpId) {
            return Flux.empty();
        }

        @Override
        public Mono<Boolean> delete(String dpId, String id) {
            return Mono.just(false);
        }
    }
}
