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

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.text.ParseException;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.time.ZoneOffset;
import java.util.List;

/**
 * The RSA keys consent tokens are signed with (ADR-006), kept in a JSON Web Key Set file so they survive restarts.
 * The first key in the set signs new tokens; the others stay to verify tokens they signed earlier, so a key is rotated
 * by putting a new key first and removing the old one once its last token has expired. Only {@link #publicJwksJson()}
 * ever leaves the service: it is served at {@code /.well-known/jwks.json} for OPA and for fiduciaries who verify tokens
 * themselves.
 */
public final class CnsTknKeys {

    private final JWKSet keys;

    private CnsTknKeys(JWKSet keys) {
        List<JWK> rsaSigningKeys = keys.getKeys().stream().filter(key -> key instanceof RSAKey && key.isPrivate()).toList();
        if (rsaSigningKeys.isEmpty() || rsaSigningKeys.size() != keys.getKeys().size()) {
            throw new IllegalArgumentException("A consent token key set holds RSA private keys only, at least one");
        }
        if (keys.getKeys().stream().anyMatch(key -> key.getKeyID() == null)) {
            throw new IllegalArgumentException("Every consent token key needs a key id (kid)");
        }
        this.keys = keys;
    }

    /**
     * Reads the key set from {@code file}; when the file does not exist and {@code createIfMissing} is set, generates one
     * key, writes it there readable by the owner only, and uses it.
     */
    public static CnsTknKeys loadOrCreate(Path file, boolean createIfMissing, Clock clock) throws IOException {
        return loadOrCreate(file, createIfMissing, clock, "cns-tkn-");
    }

    /**
     * The same, for another key set of its own, such as the receipt keys (ADR-025): {@code kidPrefix} names the keys
     * it generates, so a key's id says what it signs.
     */
    public static CnsTknKeys loadOrCreate(Path file, boolean createIfMissing, Clock clock, String kidPrefix)
            throws IOException {
        if (Files.exists(file)) {
            try {
                return new CnsTknKeys(JWKSet.parse(Files.readString(file, StandardCharsets.UTF_8)));
            } catch (ParseException e) {
                throw new IOException("Not a JSON Web Key Set: " + file, e);
            }
        }
        if (!createIfMissing) {
            throw new IOException("No consent token signing keys at " + file
                    + "; provide a key set there, or allow creating one");
        }
        CnsTknKeys created = new CnsTknKeys(new JWKSet(generate(clock, kidPrefix)));
        write(file, created.keys.toString(false));
        return created;
    }

    static RSAKey generate(Clock clock) {
        return generate(clock, "cns-tkn-");
    }

    static RSAKey generate(Clock clock, String kidPrefix) {
        String kid = kidPrefix + DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC).format(clock.instant());
        try {
            return new RSAKeyGenerator(2048).keyID(kid).keyUse(KeyUse.SIGNATURE).algorithm(JWSAlgorithm.RS256).generate();
        } catch (JOSEException e) {
            throw new IllegalStateException("Could not generate an RSA key", e);
        }
    }

    private static void write(Path file, String json) throws IOException {
        Path dir = file.toAbsolutePath().getParent();
        Files.createDirectories(dir);
        Path temp = Files.createTempFile(dir, ".cns-tkn-keys", ".tmp");
        try {
            Files.setPosixFilePermissions(temp, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException notPosix) {
            temp.toFile().setReadable(false, false);
            temp.toFile().setReadable(true, true);
        }
        Files.writeString(temp, json, StandardCharsets.UTF_8);
        Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE);
    }

    public RSAKey signingKey() {
        return (RSAKey) keys.getKeys().get(0);
    }

    /** @return the public half of the key with this id, or null when the set has no such key */
    public RSAKey publicKey(String kid) {
        JWK key = keys.getKeyByKeyId(kid);
        return key == null ? null : ((RSAKey) key).toPublicJWK();
    }

    public String publicJwksJson() {
        return keys.toPublicJWKSet().toString(true);
    }
}
