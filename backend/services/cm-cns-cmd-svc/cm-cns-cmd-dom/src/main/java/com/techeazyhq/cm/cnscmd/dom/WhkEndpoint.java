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
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * Where a fiduciary is told of its consents' changes (ADR-047): an HTTPS URL, the message types it wants (all when
 * none are named), and its signing secret, kept sealed. Rotating the secret keeps the old one in force for
 * {@link #ROTATION_OVERLAP}, so a fiduciary can switch its verifier without missing a message.
 *
 * @param secretSealed     the signing secret, sealed with the service's key; shown in plain once, when made
 * @param prevSecretSealed the secret it replaced, honoured until {@code prevSecretUntil}; null when none
 */
public record WhkEndpoint(String id, String dfId, String url, String description, Set<String> types, boolean active,
                          String secretSealed, String prevSecretSealed, Instant prevSecretUntil, Instant createdAt) {

    public static final Duration ROTATION_OVERLAP = Duration.ofHours(24);
    public static final int MAX_URL = 2_000;
    public static final int MAX_DESCRIPTION = 200;
    /** Endpoints one fiduciary may have. */
    public static final int MAX_PER_DF = 10;

    public WhkEndpoint {
        types = types == null ? Set.of() : Set.copyOf(types);
    }

    /**
     * Checks what a fiduciary gives for a new endpoint: an absolute HTTPS URL (HTTP only where {@code allowHttp}, for
     * local development) without credentials in it, a short description, and known message types.
     */
    public static void check(String url, String description, Set<String> types, boolean allowHttp) {
        if (url == null || url.isBlank() || url.length() > MAX_URL) {
            throw new IllegalArgumentException("give the endpoint's URL, at most " + MAX_URL + " characters");
        }
        URI uri;
        try {
            uri = new URI(url.strip());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("not a valid URL");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(java.util.Locale.ROOT);
        if (!scheme.equals("https") && !(allowHttp && scheme.equals("http"))) {
            throw new IllegalArgumentException("the endpoint must use https");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("the URL has no host");
        }
        if (uri.getUserInfo() != null) {
            throw new IllegalArgumentException("don't put credentials in the URL: check the signature instead");
        }
        if (uri.getFragment() != null) {
            throw new IllegalArgumentException("the URL can't have a fragment");
        }
        if (description != null && description.length() > MAX_DESCRIPTION) {
            throw new IllegalArgumentException("the description is at most " + MAX_DESCRIPTION + " characters");
        }
        if (types != null) {
            for (String t : types) {
                if (!WhkMessage.TYPES.contains(t)) {
                    throw new IllegalArgumentException("unknown message type " + t + "; known: " + WhkMessage.TYPES);
                }
            }
        }
    }

    /** Whether a message of {@code type} goes to this endpoint. Test messages always do. */
    public boolean wants(String type) {
        return active && (types.isEmpty() || types.contains(type) || WhkMessage.TEST.equals(type));
    }

    /** The sealed secrets to sign with at {@code now}: the current one, and the one it replaced while still honoured. */
    public java.util.List<String> sealedSecretsAt(Instant now) {
        return prevSecretSealed != null && prevSecretUntil != null && now.isBefore(prevSecretUntil)
                ? java.util.List.of(secretSealed, prevSecretSealed)
                : java.util.List.of(secretSealed);
    }

    public WhkEndpoint withSecret(String newSealed, Instant now) {
        return new WhkEndpoint(id, dfId, url, description, types, active, newSealed, secretSealed,
                now.plus(ROTATION_OVERLAP), createdAt);
    }

    public WhkEndpoint withActive(boolean nowActive) {
        return new WhkEndpoint(id, dfId, url, description, types, nowActive, secretSealed, prevSecretSealed,
                prevSecretUntil, createdAt);
    }
}
