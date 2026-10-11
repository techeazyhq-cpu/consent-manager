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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

class DfLogoTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16};
    private static final byte[] WEBP = "RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.ISO_8859_1);

    @Test
    void pngJpegAndWebpAreAccepted() {
        assertEquals("image/png", DfLogo.of("df-1", "image/png", PNG).contentType());
        assertEquals("image/jpeg", DfLogo.of("df-1", "image/jpeg", JPEG).contentType());
        assertEquals("image/webp", DfLogo.of("df-1", "image/webp", WEBP).contentType());
    }

    @Test
    void theBytesMustBeTheDeclaredFormat() {
        assertThrows(IllegalArgumentException.class, () -> DfLogo.of("df-1", "image/png", JPEG));
        assertThrows(IllegalArgumentException.class, () -> DfLogo.of("df-1", "image/jpeg", "<html>".getBytes()));
    }

    @Test
    void svgAndOtherTypesAreRefused() {
        byte[] svg = "<svg xmlns='http://www.w3.org/2000/svg'><script>alert(1)</script></svg>".getBytes();

        assertThrows(IllegalArgumentException.class, () -> DfLogo.of("df-1", "image/svg+xml", svg));
        assertThrows(IllegalArgumentException.class, () -> DfLogo.of("df-1", null, PNG));
    }

    @Test
    void anEmptyOrOversizedFileIsRefused() {
        byte[] oversized = Arrays.copyOf(PNG, DfLogo.MAX_BYTES + 1);

        assertThrows(IllegalArgumentException.class, () -> DfLogo.of("df-1", "image/png", new byte[0]));
        assertEquals("logo must be at most 256 KB",
                assertThrows(IllegalArgumentException.class, () -> DfLogo.of("df-1", "image/png", oversized)).getMessage());
    }

    @Test
    void theStoredBytesCannotBeChangedFromOutside() {
        byte[] bytes = PNG.clone();
        DfLogo logo = DfLogo.of("df-1", "image/png", bytes);

        bytes[0] = 0;
        logo.bytes()[1] = 0;

        assertArrayEquals(PNG, logo.bytes());
    }
}
