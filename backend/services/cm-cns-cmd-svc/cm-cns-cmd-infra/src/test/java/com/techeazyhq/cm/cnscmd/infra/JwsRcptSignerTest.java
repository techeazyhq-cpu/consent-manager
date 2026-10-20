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

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.techeazyhq.cm.cnscmd.dom.RcptSignerPort;
import com.techeazyhq.cm.seckit.CnsTknKeys;

/** A receipt verifies offline with the published receipt keys, and only with them (ADR-025). */
class JwsRcptSignerTest {

    @Test
    void aReceiptVerifiesWithThePublishedReceiptKeyAndCarriesTheRecord() throws Exception {
        Path dir = Files.createTempDirectory("rcpt-keys");
        CnsTknKeys keys = CnsTknKeys.loadOrCreate(dir.resolve("receipt-jwks.json"), true, Clock.systemUTC(), "cns-rcpt-");
        JwsRcptSigner signer = new JwsRcptSigner(keys, new ObjectMapper());
        Map<String, Object> receipt = new LinkedHashMap<>();
        receipt.put("@type", "cm:ConsentReceipt");
        receipt.put("forEvent", 2);

        RcptSignerPort.Signed signed = signer.sign(receipt);

        assertTrue(signed.kid().startsWith("cns-rcpt-"), signed.kid());
        JWSObject jws = JWSObject.parse(signed.jws());
        assertEquals("cm-receipt+json", jws.getHeader().getType().getType());
        RSAKey published = (RSAKey) JWKSet.parse(signer.publicJwksJson()).getKeyByKeyId(signed.kid());
        assertFalse(published.isPrivate(), "only public keys are published");
        assertTrue(jws.verify(new RSASSAVerifier(published)));
        assertEquals(2, ((Number) jws.getPayload().toJSONObject().get("forEvent")).intValue());

        CnsTknKeys tokenKeys = CnsTknKeys.loadOrCreate(dir.resolve("jwks.json"), true, Clock.systemUTC());
        assertFalse(jws.verify(new RSASSAVerifier(tokenKeys.signingKey().toPublicJWK())),
                "the consent token key can't stand in for the receipt key");
    }
}
