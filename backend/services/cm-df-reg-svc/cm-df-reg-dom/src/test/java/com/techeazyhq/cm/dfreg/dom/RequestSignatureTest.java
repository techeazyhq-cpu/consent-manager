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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/** ADR-041: the CM1-HMAC-SHA256 scheme, as documented in the API guide and used by the Postman collection. */
class RequestSignatureTest {

    private static final String SECRET = "cms_test-secret";
    private static final String BODY = "{\"extDpRefTyp\":\"EMAIL\"}";

    @Test
    void signsTheDocumentedLines() {
        String canonical = RequestSignature.canonical("1767225600", "0123456789abcdef", "post", "/api/cns-cmd/cns-reqs",
                null, RequestSignature.sha256Hex(BODY.getBytes(StandardCharsets.UTF_8)));

        assertEquals(String.join("\n", "CM1-HMAC-SHA256", "1767225600", "0123456789abcdef", "POST",
                "/api/cns-cmd/cns-reqs", "", RequestSignature.sha256Hex(BODY.getBytes(StandardCharsets.UTF_8))), canonical);
        String signature = RequestSignature.sign(SECRET, canonical);
        assertEquals(64, signature.length());
        assertTrue(RequestSignature.matches(SECRET, canonical, signature));
        assertFalse(RequestSignature.matches("cms_other", canonical, signature));
        assertFalse(RequestSignature.matches(SECRET, canonical, signature.toUpperCase()), "lower-case hex only");
    }

    @Test
    void anyChangeToTheRequestBreaksTheSignature() {
        String body = RequestSignature.sha256Hex(BODY.getBytes(StandardCharsets.UTF_8));
        String signed = RequestSignature.sign(SECRET, RequestSignature.canonical("1", "0123456789abcdef", "POST", "/a", "x=1", body));
        assertNotEquals(signed, RequestSignature.sign(SECRET, RequestSignature.canonical("1", "0123456789abcdef", "POST", "/a", "x=2", body)));
        assertNotEquals(signed, RequestSignature.sign(SECRET, RequestSignature.canonical("1", "0123456789abcdef", "PUT", "/a", "x=1", body)));
        assertNotEquals(signed, RequestSignature.sign(SECRET, RequestSignature.canonical("1", "0123456789abcdeX", "POST", "/a", "x=1", body)));
        assertNotEquals(signed, RequestSignature.sign(SECRET, RequestSignature.canonical("1", "0123456789abcdef", "POST", "/a", "x=1",
                RequestSignature.sha256Hex("{}".getBytes(StandardCharsets.UTF_8)))));
    }

    @Test
    void nonceAndTimestampHaveAShape() {
        assertTrue(RequestSignature.isWellFormedNonce("a1b2c3d4e5f6a7b8"));
        assertFalse(RequestSignature.isWellFormedNonce("short"));
        assertFalse(RequestSignature.isWellFormedNonce("has spaces in it!!"));
        assertTrue(RequestSignature.isWellFormedTimestamp("1767225600"));
        assertFalse(RequestSignature.isWellFormedTimestamp("2026-10-19T00:00:00Z"));
    }

    @Test
    void anIssuedKeyKeepsOnlyItsHash_andItsSecretSealed() {
        SecretSealerPort sealer = new SecretSealerPort() {
            public String seal(String s) {
                return "sealed:" + s;
            }

            public String open(String s) {
                return s.substring(7);
            }
        };
        DfApiKey.Issued issued = DfApiKey.issue("df-1", "Orders backend", true, "u-1", sealer);

        assertTrue(issued.apiKey().startsWith("cmk_"));
        assertTrue(issued.signingSecret().startsWith("cms_"));
        assertEquals(DfApiKey.hash(issued.apiKey()), issued.key().keyHash());
        assertEquals(issued.apiKey().substring(0, 12), issued.key().keyPrefix());
        assertEquals(issued.signingSecret(), sealer.open(issued.key().sealedSigningSecret()));
        assertFalse(issued.key().keyHash().contains(issued.apiKey()));
    }
}
