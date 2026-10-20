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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

/** Webhooks never reach the platform's own network: no SSRF through an endpoint (ADR-047). */
class WebClientWhkSenderTest {

    @ParameterizedTest
    @ValueSource(strings = {"127.0.0.1", "10.1.2.3", "172.16.0.1", "192.168.1.1", "169.254.169.254", "100.64.0.1",
            "0.0.0.0", "192.0.0.8", "198.18.0.1", "224.0.0.1", "::1", "fe80::1", "fd00::1", "::ffff:10.0.0.1"})
    void internalAddressesAreRefused(String ip) throws Exception {
        assertTrue(WebClientWhkSender.isInternal(InetAddress.getByName(ip)), ip);
    }

    @ParameterizedTest
    @ValueSource(strings = {"8.8.8.8", "1.1.1.1", "100.128.0.1", "2001:4860:4860::8888"})
    void publicAddressesAreAllowed(String ip) throws Exception {
        assertFalse(WebClientWhkSender.isInternal(InetAddress.getByName(ip)), ip);
    }

    @Test
    void refusesLoopbackUnlessAllowedAndNeverSendsThere() {
        WebClientWhkSender strict = new WebClientWhkSender(WebClient.builder(), false);
        assertTrue(strict.refusal("https://127.0.0.1/h").block().isPresent());
        assertTrue(strict.refusal("https://localhost/h").block().isPresent());
        assertTrue(strict.refusal("https://no-such-host.invalid/h").block().isPresent());
        assertEquals(Optional.empty(), new WebClientWhkSender(WebClient.builder(), true).refusal("http://127.0.0.1/h").block());

        var result = strict.post("https://127.0.0.1:1/h", Map.of(), "{}").block();
        assertEquals(null, result.status());
        assertTrue(result.error().startsWith("refused:"));
    }
}
