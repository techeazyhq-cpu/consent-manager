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
 * answers "what has been consented". The signing key itself is a boot-level/composition-root
 * concern (plan §5's Clean Architecture layering) — this class just signs/verifies with whatever
 * {@link RSAKey} it is given.
 */
public class CnsTknSigner {

    private final RSAKey signingKey;

    public CnsTknSigner(RSAKey signingKey) {
        this.signingKey = signingKey;
    }

    public String sign(CnsTknClaims claims) {
        try {
            JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
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
            JWSVerifier verifier = new RSASSAVerifier(signingKey.toRSAPublicKey());
            if (!signedJwt.verify(verifier)) {
                throw new IllegalArgumentException("CnsTkn signature verification failed");
            }

            JWTClaimsSet claims = signedJwt.getJWTClaimsSet();
            @SuppressWarnings("unchecked")
            List<String> scope = (List<String>) (List<?>) claims.getStringListClaim("scope");

            return new CnsTknClaims(
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
