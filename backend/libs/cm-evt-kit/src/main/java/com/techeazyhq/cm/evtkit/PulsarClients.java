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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;

/**
 * The one place every service makes its Pulsar client (ADR-099). With nothing set it is the plain client it always was. A
 * {@code pulsar+ssl://} URL is verified against the CA bundle in {@code CM_PULSAR_TLS_TRUST_CERTS_FILE} (and the broker's host name is
 * checked), and {@code CM_PULSAR_AUTH_TOKEN} is sent as a token. Neither value is ever logged.
 */
public final class PulsarClients {

    static final String TRUST_CERTS_ENV = "CM_PULSAR_TLS_TRUST_CERTS_FILE";
    static final String AUTH_TOKEN_ENV = "CM_PULSAR_AUTH_TOKEN";
    static final String TOKEN_AUTH_PLUGIN = "org.apache.pulsar.client.impl.auth.AuthenticationToken";

    private PulsarClients() {
    }

    /** The client for this process, from its environment. */
    public static PulsarClient create(String serviceUrl) throws PulsarClientException {
        return PulsarClient.builder().loadConf(settings(serviceUrl, System::getenv)).build();
    }

    /** The client configuration for a service URL and an environment; a function so a test needs no real environment. */
    static Map<String, Object> settings(String serviceUrl, Function<String, String> env) {
        Map<String, Object> conf = new LinkedHashMap<>();
        conf.put("serviceUrl", serviceUrl);
        String trust = blankToNull(env.apply(TRUST_CERTS_ENV));
        if (trust != null) {
            conf.put("tlsTrustCertsFilePath", trust);
            conf.put("tlsHostnameVerificationEnable", true);
        }
        String token = blankToNull(env.apply(AUTH_TOKEN_ENV));
        if (token != null) {
            conf.put("authPluginClassName", TOKEN_AUTH_PLUGIN);
            conf.put("authParams", "token:" + token);
        }
        return conf;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
