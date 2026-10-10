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

import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/**
 * Signs and verifies the {@code CnsTkn} (Consent Token) — a self-contained, offline-verifiable
 * JWS a Data Fiduciary can validate without calling back to CM (plan §7c step 3, §9a). Deliberately
 * independent of Keycloak's own signing key: Keycloak's JWT answers "who is calling"; this one
 * answers "what has been consented". Tokens are signed with the first key of {@link CnsTknKeys} and name it in
 * {@code kid}, so any key still in the set verifies the tokens it signed; the issuer is {@value #ISSUER}.
 */
public class CnsTknSigner {

    public static final String ISSUER = "cm-cns-cmd-svc";

    private final CnsTknKeys keys;

    public CnsTknSigner(CnsTknKeys keys) {
        this.keys = keys;
    }

    public String sign(CnsTknClaims claims) {
        try {
            RSAKey signingKey = keys.signingKey();
            JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                    .issuer(ISSUER)
                    .issueTime(new Date())
                    .claim("cns_art_id", claims.consentArtefactId())
                    .claim("sub_did", claims.subDid())
                    .claim("fiduciary_id", claims.fiduciaryId())
                    .claim("purpose_code", claims.purposeCode())
                    .claim("access_type", claims.accessType().name())
                    .claim("scope", claims.scope())
                    .expirationTime(Date.from(claims.exp()));

            SignedJWT signedJwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.getKeyID()).build(),
                    builder.build());

            JWSSigner signer = new RSASSASigner(signingKey);
            signedJwt.sign(signer);
            return signedJwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("Failed to sign CnsTkn", e);
        }
    }

    public CnsTknClaims verify(String compactJws) {
        try {
            SignedJWT signedJwt = SignedJWT.parse(compactJws);
            if (!JWSAlgorithm.RS256.equals(signedJwt.getHeader().getAlgorithm())) {
                throw new IllegalArgumentException("CnsTkn must be signed with RS256");
            }
            RSAKey publicKey = keys.publicKey(signedJwt.getHeader().getKeyID());
            if (publicKey == null) {
                throw new IllegalArgumentException("CnsTkn signed with an unknown key");
            }
            JWSVerifier verifier = new RSASSAVerifier(publicKey.toRSAPublicKey());
            if (!signedJwt.verify(verifier)) {
                throw new IllegalArgumentException("CnsTkn signature verification failed");
            }

            JWTClaimsSet claims = signedJwt.getJWTClaimsSet();
            if (!ISSUER.equals(claims.getIssuer())) {
                throw new IllegalArgumentException("CnsTkn has the wrong issuer");
            }
            @SuppressWarnings("unchecked")
            List<String> scope = (List<String>) (List<?>) claims.getStringListClaim("scope");

            return new CnsTknClaims(
                    claims.getStringClaim("cns_art_id"),
                    claims.getStringClaim("sub_did"),
                    claims.getStringClaim("fiduciary_id"),
                    claims.getStringClaim("purpose_code"),
                    CnsTknClaims.AccessType.valueOf(claims.getStringClaim("access_type")),
                    scope,
                    Instant.ofEpochMilli(claims.getExpirationTime().getTime()));
        } catch (ParseException | JOSEException e) {
            throw new IllegalArgumentException("Malformed or invalid CnsTkn", e);
        }
    }
}
