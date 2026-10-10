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

package com.techeazyhq.cm.seckit;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CnsTknKeysTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-10T12:00:00Z"), ZoneOffset.UTC);
    private static final Instant IN_AN_HOUR = Instant.now().plusSeconds(3600);

    @TempDir
    Path dir;

    private static CnsTknClaims claims(String artefactId) {
        return new CnsTknClaims(artefactId, "dp-1", "df-1", "KYC", CnsTknClaims.AccessType.ALLOW, List.of(), IN_AN_HOUR);
    }

    @Test
    void createsAKeyOnFirstStartAndReusesItAfterARestart() throws IOException {
        Path file = dir.resolve("keys/jwks.json");
        CnsTknKeys first = CnsTknKeys.loadOrCreate(file, true, CLOCK);
        String token = new CnsTknSigner(first).sign(claims("art-1"));

        CnsTknKeys afterRestart = CnsTknKeys.loadOrCreate(file, true, CLOCK);

        assertEquals(first.signingKey().getKeyID(), afterRestart.signingKey().getKeyID());
        assertEquals("cns-tkn-20261010T120000Z", afterRestart.signingKey().getKeyID());
        assertEquals("art-1", new CnsTknSigner(afterRestart).verify(token).consentArtefactId());
    }

    @Test
    void refusesToStartWithoutKeysWhenCreatingThemIsNotAllowed() {
        assertThrows(IOException.class, () -> CnsTknKeys.loadOrCreate(dir.resolve("absent.json"), false, CLOCK));
    }

    @Test
    void publishesOnlyThePublicHalves() throws Exception {
        CnsTknKeys keys = CnsTknKeys.loadOrCreate(dir.resolve("jwks.json"), true, CLOCK);

        JWKSet published = JWKSet.parse(keys.publicJwksJson());

        assertEquals(1, published.getKeys().size());
        assertFalse(published.getKeys().get(0).isPrivate());
        assertFalse(keys.publicJwksJson().contains("\"d\""));
    }

    @Test
    void anOlderKeyStillVerifiesTheTokensItSignedAfterRotation() throws Exception {
        Path file = dir.resolve("jwks.json");
        CnsTknKeys before = CnsTknKeys.loadOrCreate(file, true, CLOCK);
        String oldToken = new CnsTknSigner(before).sign(claims("art-old"));
        RSAKey newKey = CnsTknKeys.generate(Clock.offset(CLOCK, java.time.Duration.ofDays(90)));
        Files.writeString(file, new JWKSet(List.of(newKey, before.signingKey())).toString(false));

        CnsTknKeys rotated = CnsTknKeys.loadOrCreate(file, false, CLOCK);
        CnsTknSigner signer = new CnsTknSigner(rotated);

        assertEquals(newKey.getKeyID(), rotated.signingKey().getKeyID());
        assertEquals("art-old", signer.verify(oldToken).consentArtefactId());
        assertEquals("art-new", signer.verify(signer.sign(claims("art-new"))).consentArtefactId());
    }

    @Test
    void aTokenFromAKeyOutsideTheSetIsRejectedEvenWithTheSameKid() throws Exception {
        CnsTknKeys ours = CnsTknKeys.loadOrCreate(dir.resolve("ours.json"), true, CLOCK);
        CnsTknKeys theirs = CnsTknKeys.loadOrCreate(dir.resolve("theirs.json"), true, CLOCK);
        assertEquals(ours.signingKey().getKeyID(), theirs.signingKey().getKeyID());

        String forged = new CnsTknSigner(theirs).sign(claims("art-1"));

        assertThrows(IllegalArgumentException.class, () -> new CnsTknSigner(ours).verify(forged));
    }

    @Test
    void theTokenCarriesItsArtefactIssuerAndKeyId() throws Exception {
        CnsTknKeys keys = CnsTknKeys.loadOrCreate(dir.resolve("jwks.json"), true, CLOCK);

        var jwt = com.nimbusds.jwt.SignedJWT.parse(new CnsTknSigner(keys).sign(claims("art-9")));

        assertEquals(keys.signingKey().getKeyID(), jwt.getHeader().getKeyID());
        assertEquals(CnsTknSigner.ISSUER, jwt.getJWTClaimsSet().getIssuer());
        assertEquals("art-9", jwt.getJWTClaimsSet().getStringClaim("cns_art_id"));
        assertNotNull(jwt.getJWTClaimsSet().getIssueTime());
        assertTrue(jwt.getJWTClaimsSet().getExpirationTime().toInstant().isAfter(Instant.now()));
        assertNull(keys.publicKey("no-such-kid"));
    }
}
