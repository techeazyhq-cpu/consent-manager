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

package com.techeazyhq.cm.dfreg.infra;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import com.techeazyhq.cm.dfreg.dom.SecretSealerPort;

/**
 * AES-256-GCM sealing for secrets df-reg must read back, such as request signing secrets (ADR-041). A sealed value is
 * {@code v1:} + base64(12-byte IV + ciphertext + tag). The key comes from {@code cm.dfreg.secrets-key} (base64 of 32
 * bytes), which production takes from the cloud's key management (AWS KMS, Azure Key Vault, GCP KMS).
 */
public class AesGcmSecretSealer implements SecretSealerPort {

    private static final String VERSION = "v1:";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKeySpec key;

    public AesGcmSecretSealer(String base64Key) {
        byte[] bytes = Base64.getDecoder().decode(base64Key == null ? "" : base64Key.trim());
        if (bytes.length != 32) {
            throw new IllegalStateException("cm.dfreg.secrets-key must be base64 of exactly 32 bytes");
        }
        this.key = new SecretKeySpec(bytes, "AES");
    }

    @Override
    public String seal(String secret) {
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] sealed = cipher.doFinal(secret.getBytes(StandardCharsets.UTF_8));
            return VERSION + Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + sealed.length)
                    .put(iv).put(sealed).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not seal a secret", e);
        }
    }

    @Override
    public String open(String sealed) {
        if (sealed == null || !sealed.startsWith(VERSION)) {
            throw new IllegalStateException("Not a sealed secret");
        }
        try {
            byte[] all = Base64.getDecoder().decode(sealed.substring(VERSION.length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, all, 0, 12));
            return new String(cipher.doFinal(all, 12, all.length - 12), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not open a sealed secret: wrong key, or altered", e);
        }
    }
}
