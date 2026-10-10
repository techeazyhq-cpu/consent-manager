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

package com.techeazyhq.cm.ntf.infra;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * notification-service's replay-protection signature ({@code NS1-HMAC-SHA256}, its client guide and ADR-036):
 * lower-case hex HMAC-SHA256, keyed by the client's signing secret, over seven lines — scheme, timestamp, nonce,
 * method, path, query and the SHA-256 of the exact body bytes — joined by {@code \n} with no trailing line feed.
 */
final class NtfRequestSigner {

    static final String SCHEME = "NS1-HMAC-SHA256";

    private NtfRequestSigner() {
    }

    static String sign(String secret, long timestamp, String nonce, String method, String path, String query, byte[] body) {
        String canonical = String.join("\n", SCHEME, Long.toString(timestamp), nonce, method, path,
                query == null ? "" : query, sha256Hex(body));
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }

    private static String sha256Hex(byte[] body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
