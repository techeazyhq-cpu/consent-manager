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
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * How a fiduciary's system signs an API request (ADR-041), the same scheme as TechEazyHQ's notification-service
 * (its ADR-036) under its own name. The signature binds the method, path, query and the exact body bytes to a
 * timestamp and a single-use nonce, so a captured request can be neither altered nor sent again.
 *
 * <p>The signed text is these lines joined by a line feed, with no trailing line feed:
 * <pre>
 * CM1-HMAC-SHA256
 * {timestamp: Unix seconds, as sent in X-Signature-Timestamp}
 * {nonce: as sent in X-Signature-Nonce}
 * {HTTP method, upper case}
 * {path, exactly as sent, without the query}
 * {query string, exactly as sent, without the '?'; empty if none}
 * {lower-case hex SHA-256 of the body bytes; of no bytes if there is no body}
 * </pre>
 * The signature, sent in X-Signature, is the lower-case hex HMAC-SHA256 of that text keyed by the signing secret's
 * UTF-8 bytes.
 */
public final class RequestSignature {

    public static final String SCHEME = "CM1-HMAC-SHA256";

    private static final String HMAC = "HmacSHA256";
    private static final Pattern NONCE = Pattern.compile("[A-Za-z0-9_-]{16,64}");
    private static final Pattern TIMESTAMP = Pattern.compile("\\d{1,12}");
    private static final Pattern HEX64 = Pattern.compile("[0-9a-f]{64}");
    private static final HexFormat HEX = HexFormat.of();

    private RequestSignature() {
    }

    /** The text a client signs; see the class description. {@code bodySha256} is the lower-case hex body digest. */
    public static String canonical(String timestamp, String nonce, String method, String path, String query,
                                   String bodySha256) {
        return String.join("\n", SCHEME, timestamp, nonce, method.toUpperCase(Locale.ROOT), path,
                query == null ? "" : query, bodySha256);
    }

    public static String sign(String secret, String canonical) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC));
            return HEX.formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 is not available", e);
        }
    }

    /** Whether {@code presented} signs {@code canonical}, compared in constant time. */
    public static boolean matches(String secret, String canonical, String presented) {
        if (!isHex64(presented)) {
            return false;
        }
        return MessageDigest.isEqual(sign(secret, canonical).getBytes(StandardCharsets.US_ASCII),
                presented.getBytes(StandardCharsets.US_ASCII));
    }

    public static boolean isWellFormedNonce(String nonce) {
        return nonce != null && NONCE.matcher(nonce).matches();
    }

    public static boolean isWellFormedTimestamp(String timestamp) {
        return timestamp != null && TIMESTAMP.matcher(timestamp).matches();
    }

    public static boolean isHex64(String value) {
        return value != null && HEX64.matcher(value).matches();
    }

    public static String sha256Hex(byte[] bytes) {
        try {
            return HEX.formatHex(MessageDigest.getInstance("SHA-256").digest(bytes == null ? new byte[0] : bytes));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
