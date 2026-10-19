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

package com.techeazyhq.cm.dfreg.dom;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * An API key a fiduciary's system calls the API with (ADR-041), and optionally the secret it signs requests with.
 *
 * <ul>
 *   <li>The key ({@code cmk_} + 256 random bits) is shown once. Only its SHA-256 is kept, with its first characters
 *   to tell keys apart.</li>
 *   <li>The signing secret ({@code cms_} + 256 random bits) is also shown once. It is kept sealed (AES-GCM), since
 *   every signature has to be recomputed with it.</li>
 *   <li>With {@code signingRequired}, an unsigned write (POST, PUT, PATCH, DELETE) is refused. A signed request is
 *   always checked, so a system can start signing before signing is required.</li>
 *   <li>A revoked key stops working at once and can't be restored; issue another.</li>
 * </ul>
 */
public final class DfApiKey {

    public static final String KEY_PREFIX = "cmk_";
    public static final String SECRET_PREFIX = "cms_";
    static final int SHOWN = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final String id;
    private final String dfId;
    private final String label;
    private final String keyPrefix;
    private final String keyHash;
    private final String sealedSigningSecret;
    private boolean signingRequired;
    private final Instant createdAt;
    private final String createdBy;
    private final Instant lastUsedAt;
    private Instant revokedAt;

    private DfApiKey(String id, String dfId, String label, String keyPrefix, String keyHash, String sealedSigningSecret,
                     boolean signingRequired, Instant createdAt, String createdBy, Instant lastUsedAt, Instant revokedAt) {
        this.id = id;
        this.dfId = dfId;
        this.label = label;
        this.keyPrefix = keyPrefix;
        this.keyHash = keyHash;
        this.sealedSigningSecret = sealedSigningSecret;
        this.signingRequired = signingRequired;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
        this.lastUsedAt = lastUsedAt;
        this.revokedAt = revokedAt;
    }

    /** A new key, with the key and signing secret in the clear for the one time they are shown. */
    public record Issued(DfApiKey key, String apiKey, String signingSecret) {
    }

    public static Issued issue(String dfId, String label, boolean signingRequired, String createdBy,
                               SecretSealerPort sealer) {
        String name = label == null ? "" : label.trim();
        if (name.isEmpty() || name.length() > 64) {
            throw new IllegalArgumentException("Name the key (at most 64 characters), so you can tell it apart later");
        }
        String apiKey = KEY_PREFIX + random();
        String secret = SECRET_PREFIX + random();
        DfApiKey key = new DfApiKey(UUID.randomUUID().toString(), dfId, name, apiKey.substring(0, SHOWN),
                hash(apiKey), sealer.seal(secret), signingRequired, Instant.now(), createdBy, null, null);
        return new Issued(key, apiKey, secret);
    }

    public static DfApiKey rehydrate(String id, String dfId, String label, String keyPrefix, String keyHash,
                                     String sealedSigningSecret, boolean signingRequired, Instant createdAt,
                                     String createdBy, Instant lastUsedAt, Instant revokedAt) {
        return new DfApiKey(id, dfId, label, keyPrefix, keyHash, sealedSigningSecret, signingRequired, createdAt,
                createdBy, lastUsedAt, revokedAt);
    }

    /** What a presented key is looked up by: never the key itself. */
    public static String hash(String apiKey) {
        return RequestSignature.sha256Hex(apiKey.getBytes(StandardCharsets.UTF_8));
    }

    public void revoke() {
        if (revokedAt == null) {
            revokedAt = Instant.now();
        }
    }

    public void requireSigning(boolean required) {
        if (revokedAt != null) {
            throw new IllegalStateException("This key is revoked");
        }
        this.signingRequired = required;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    private static String random() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String id() {
        return id;
    }

    public String dfId() {
        return dfId;
    }

    public String label() {
        return label;
    }

    public String keyPrefix() {
        return keyPrefix;
    }

    public String keyHash() {
        return keyHash;
    }

    public String sealedSigningSecret() {
        return sealedSigningSecret;
    }

    public boolean signingRequired() {
        return signingRequired;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public String createdBy() {
        return createdBy;
    }

    public Instant lastUsedAt() {
        return lastUsedAt;
    }

    public Instant revokedAt() {
        return revokedAt;
    }
}
