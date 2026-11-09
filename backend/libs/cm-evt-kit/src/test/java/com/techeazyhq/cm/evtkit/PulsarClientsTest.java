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

package com.techeazyhq.cm.evtkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

/** The Pulsar client is plain unless told otherwise, and TLS and a token come only from the environment (ADR-099). */
class PulsarClientsTest {

    private static Map<String, Object> with(Map<String, String> env) {
        return PulsarClients.settings("pulsar+ssl://broker:6651", env::get);
    }

    @Test
    void withNothingSetItIsTheServiceUrlAndNothingElse() {
        Map<String, Object> conf = with(Map.of());
        assertEquals(Map.of("serviceUrl", "pulsar+ssl://broker:6651"), conf);
    }

    @Test
    void aTrustBundleTurnsOnVerificationAgainstItAndTheHostName() {
        Map<String, Object> conf = with(Map.of("CM_PULSAR_TLS_TRUST_CERTS_FILE", "/etc/pulsar-ca/ca-bundle.pem"));
        assertEquals("/etc/pulsar-ca/ca-bundle.pem", conf.get("tlsTrustCertsFilePath"));
        assertEquals(true, conf.get("tlsHostnameVerificationEnable"));
        assertFalse(conf.containsKey("authPluginClassName"));
    }

    @Test
    void aTokenIsSentWithTheTokenPlugin() {
        Map<String, Object> conf = with(Map.of("CM_PULSAR_AUTH_TOKEN", "  abc.def  "));
        assertEquals(PulsarClients.TOKEN_AUTH_PLUGIN, conf.get("authPluginClassName"));
        assertEquals("token:abc.def", conf.get("authParams"));
        assertFalse(conf.containsKey("tlsTrustCertsFilePath"));
    }

    @Test
    void blankValuesCountAsNotSet() {
        Map<String, Object> conf = with(Map.of("CM_PULSAR_TLS_TRUST_CERTS_FILE", " ", "CM_PULSAR_AUTH_TOKEN", ""));
        assertEquals(1, conf.size());
    }

    @Test
    void theConfigurationIsOneTheClientBuilderAccepts() throws Exception {
        // loadConf rejects an unknown key; no connection is made until the client is used
        try (var client = org.apache.pulsar.client.api.PulsarClient.builder()
                .loadConf(PulsarClients.settings("pulsar://localhost:6650", Map.of(
                        "CM_PULSAR_TLS_TRUST_CERTS_FILE", "/nonexistent/ca.pem", "CM_PULSAR_AUTH_TOKEN", "t")::get))
                .build()) {
            assertTrue(client != null);
        }
    }
}
