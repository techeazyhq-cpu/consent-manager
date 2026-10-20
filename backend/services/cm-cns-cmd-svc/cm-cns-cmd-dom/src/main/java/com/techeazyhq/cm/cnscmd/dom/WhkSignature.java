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

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Webhook signatures, as the Standard Webhooks specification defines them (ADR-047), so fiduciaries can verify with
 * that specification's libraries in any language:
 * <ul>
 *   <li>a secret is {@code whsec_} and base64 of 24 random bytes; the key is those bytes</li>
 *   <li>the signed content is {@code webhook-id.webhook-timestamp.body}</li>
 *   <li>{@code webhook-signature} is {@code v1,} and base64 of its HMAC-SHA256, one per secret in force, separated by
 *       spaces: while a rotated-out secret is still honoured, both are sent</li>
 * </ul>
 */
public final class WhkSignature {

    public static final String PREFIX = "whsec_";
    private static final SecureRandom RANDOM = new SecureRandom();

    private WhkSignature() {
    }

    public static String newSecret() {
        byte[] key = new byte[24];
        RANDOM.nextBytes(key);
        return PREFIX + Base64.getEncoder().encodeToString(key);
    }

    /** The {@code webhook-signature} header for {@code body}, signed with each of {@code secrets}. */
    public static String header(List<String> secrets, String msgId, long timestamp, String body) {
        return secrets.stream().map(s -> "v1," + sign(s, msgId, timestamp, body)).collect(Collectors.joining(" "));
    }

    static String sign(String secret, String msgId, long timestamp, String body) {
        if (secret == null || !secret.startsWith(PREFIX)) {
            throw new IllegalArgumentException("not a webhook secret");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(Base64.getDecoder().decode(secret.substring(PREFIX.length())), "HmacSHA256"));
            byte[] digest = mac.doFinal((msgId + "." + timestamp + "." + body).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 is always available", e);
        }
    }
}
