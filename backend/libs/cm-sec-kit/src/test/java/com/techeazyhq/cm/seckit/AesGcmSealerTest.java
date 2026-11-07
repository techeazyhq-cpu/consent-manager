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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

class AesGcmSealerTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);
    private final AesGcmSealer sealer = new AesGcmSealer(KEY, "test.key");

    @Test
    void aSealedSecretOpensToItselfAndIsSealedAfreshEachTime() {
        String first = sealer.seal("s3cret-ü");
        String second = sealer.seal("s3cret-ü");

        assertTrue(first.startsWith("v1:"));
        assertNotEquals(first, second, "a new IV each time");
        assertEquals("s3cret-ü", sealer.open(first));
        assertEquals("s3cret-ü", sealer.open(second));
    }

    @Test
    void anAlteredValueOrAnotherKeyDoesNotOpen() {
        String sealed = sealer.seal("s3cret");
        byte[] raw = Base64.getDecoder().decode(sealed.substring(3));
        raw[raw.length - 1] ^= 1;
        String altered = "v1:" + Base64.getEncoder().encodeToString(raw);
        byte[] otherKey = new byte[32];
        otherKey[0] = 1;
        AesGcmSealer other = new AesGcmSealer(Base64.getEncoder().encodeToString(otherKey), "test.key");

        assertThrows(IllegalStateException.class, () -> sealer.open(altered));
        assertThrows(IllegalStateException.class, () -> other.open(sealed));
        assertThrows(IllegalStateException.class, () -> sealer.open("plain"));
    }

    @Test
    void aKeyOfTheWrongLengthIsRefusedNamingItsSetting() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new AesGcmSealer(Base64.getEncoder().encodeToString(new byte[16]), "cm.some.key"));
        assertTrue(e.getMessage().contains("cm.some.key"));
    }

    /** The provider is shared; each call makes its own Cipher, so concurrent use is safe. */
    @Test
    void manyThreadsSealAndOpenAtOnce() throws Exception {
        try (var pool = Executors.newFixedThreadPool(8)) {
            List<Callable<Boolean>> work = IntStream.range(0, 400).<Callable<Boolean>>mapToObj(i -> () -> {
                String secret = "secret-" + i;
                return secret.equals(sealer.open(sealer.seal(secret)));
            }).toList();
            for (var result : pool.invokeAll(work)) {
                assertTrue(result.get());
            }
        }
    }
}
