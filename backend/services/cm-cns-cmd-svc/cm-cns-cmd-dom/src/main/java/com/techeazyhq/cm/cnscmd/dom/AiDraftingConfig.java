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

import java.net.URI;
import java.time.Instant;
import java.util.Locale;

/**
 * Which model drafts purposes, set by the platform's staff in CM Admin (ADR-113). One record for the platform. {@code apiKeySealed} is the key
 * sealed with this service's key: it is never returned, never logged and never put in the audit trail.
 *
 * <p>{@code thirdPartyAck} is the operator's recorded choice to send fiduciaries' descriptions to a party that is not the platform's own
 * infrastructure (ADR-111 §2: never without the operator choosing it). It is required whenever {@link #leavesPlatform()}.
 */
public record AiDraftingConfig(boolean enabled, AiProvider provider, String endpoint, String model, String apiKeySealed, int timeoutSeconds,
                               int maxTokens, boolean thirdPartyAck, String updatedBy, Instant updatedAt) {

    public static final int MAX_MODEL = 120;
    public static final int MIN_TIMEOUT = 5;
    public static final int MAX_TIMEOUT = 120;
    public static final int MIN_TOKENS = 256;
    public static final int MAX_TOKENS = 8000;

    /** Nothing configured: drafting is off. */
    public static AiDraftingConfig off() {
        return new AiDraftingConfig(false, AiProvider.OLLAMA, AiProvider.OLLAMA.defaultEndpoint(), AiProvider.OLLAMA.defaultModel(), null, 100,
                1500, false, null, null);
    }

    public boolean hasKey() {
        return apiKeySealed != null && !apiKeySealed.isBlank();
    }

    /**
     * Whether descriptions would leave the platform's own infrastructure: a vendor's API always, and a server of the operator's only when its
     * address is not a loopback, private or single-label (cluster-internal) host.
     */
    public boolean leavesPlatform() {
        return leavesPlatform(provider, endpoint);
    }

    public static boolean leavesPlatform(AiProvider provider, String endpoint) {
        if (!provider.endpointEditable()) {
            return true;
        }
        String host = hostOf(endpoint);
        return host == null || !isOwnHost(host);
    }

    /** Checks the whole configuration; throws {@link IllegalArgumentException} naming what is wrong (the app layer makes it a 400 {@code invalid-ai-config}). */
    public AiDraftingConfig validated() {
        if (provider == null) {
            throw bad("Choose a provider");
        }
        if (model == null || model.isBlank() || model.strip().length() > MAX_MODEL || model.chars().anyMatch(Character::isISOControl)) {
            throw bad("Name the model, in at most " + MAX_MODEL + " characters");
        }
        if (timeoutSeconds < MIN_TIMEOUT || timeoutSeconds > MAX_TIMEOUT) {
            throw bad("The time to wait for the model is " + MIN_TIMEOUT + " to " + MAX_TIMEOUT + " seconds");
        }
        if (maxTokens < MIN_TOKENS || maxTokens > MAX_TOKENS) {
            throw bad("The longest reply is " + MIN_TOKENS + " to " + MAX_TOKENS + " tokens");
        }
        checkEndpoint(provider, endpoint);
        if (enabled && provider.keyRequired() && !hasKey()) {
            throw bad(provider.label() + " needs an API key");
        }
        if (enabled && leavesPlatform() && !thirdPartyAck) {
            throw bad("Confirm that fiduciaries' descriptions may be sent to " + (provider.endpointEditable() ? hostOf(endpoint) : provider.label()));
        }
        return this;
    }

    /** An address: http or https, a host, and nothing else; a vendor's API must be https and exactly the vendor's; never a link-local address. */
    public static void checkEndpoint(AiProvider provider, String endpoint) {
        if (!provider.endpointEditable()) {
            if (endpoint != null && !endpoint.isBlank() && !trimSlash(endpoint).equals(provider.defaultEndpoint())) {
                throw bad(provider.label() + " is reached at " + provider.defaultEndpoint() + " only");
            }
            return;
        }
        URI uri;
        try {
            uri = URI.create(endpoint == null ? "" : endpoint.strip());
        } catch (IllegalArgumentException e) {
            throw bad("The address is not a valid URL");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw bad("The address starts with http:// or https://");
        }
        if (uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
            throw bad("The address is a host and port only: no user, query or fragment");
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (host.startsWith("169.254.") || host.equals("metadata.google.internal") || host.startsWith("fe80:") || host.startsWith("[fe80:")) {
            throw bad("That address is a cloud metadata address, not a model");
        }
        if (scheme.equals("http") && !isOwnHost(host)) {
            throw bad("A server outside your own network is reached over https");
        }
    }

    /** The endpoint to call: a vendor's fixed one, or the operator's without a trailing slash. */
    public String effectiveEndpoint() {
        return provider.endpointEditable() ? trimSlash(endpoint) : provider.defaultEndpoint();
    }

    private static boolean isOwnHost(String host) {
        String h = host.toLowerCase(Locale.ROOT);
        if (h.startsWith("[") && h.endsWith("]")) {
            h = h.substring(1, h.length() - 1);
        }
        if (h.equals("localhost") || h.equals("::1") || h.endsWith(".localhost") || h.endsWith(".internal") || h.endsWith(".local")
                || h.endsWith(".svc") || h.contains(".svc.") || !h.contains(".")) {
            return true;
        }
        if (h.startsWith("127.") || h.startsWith("10.") || h.startsWith("192.168.")) {
            return true;
        }
        if (h.startsWith("172.")) {
            String[] parts = h.split("\\.");
            try {
                int second = Integer.parseInt(parts[1]);
                return second >= 16 && second <= 31;
            } catch (RuntimeException e) {
                return false;
            }
        }
        return false;
    }

    private static String hostOf(String endpoint) {
        try {
            return endpoint == null ? null : URI.create(endpoint.strip()).getHost();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String trimSlash(String s) {
        String t = s == null ? "" : s.strip();
        return t.endsWith("/") ? t.substring(0, t.length() - 1) : t;
    }

    private static IllegalArgumentException bad(String message) {
        return new IllegalArgumentException(message);
    }
}
