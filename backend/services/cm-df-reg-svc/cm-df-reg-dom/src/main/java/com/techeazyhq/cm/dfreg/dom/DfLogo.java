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

import java.time.Instant;
import java.util.Arrays;
import java.util.Map;

/**
 * A Data Fiduciary's company logo, shown on the CM Admin dashboard and to data principals. Only raster formats are
 * accepted (SVG can carry script), at most {@link #MAX_BYTES}, and the bytes must actually be the declared format:
 * the content type is checked against the file's signature, so a file can't pass as an image by its name alone.
 */
public record DfLogo(String dfId, String contentType, byte[] bytes, Instant updatedAt) {

    public static final int MAX_BYTES = 256 * 1024;

    private static final Map<String, byte[]> SIGNATURES = Map.of(
            "image/png", new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'},
            "image/jpeg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});

    public DfLogo {
        if (dfId == null || dfId.isBlank()) {
            throw new IllegalArgumentException("dfId must not be blank");
        }
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("logo must not be empty");
        }
        if (bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException("logo must be at most " + MAX_BYTES / 1024 + " KB");
        }
        if (!hasSignatureOf(contentType, bytes)) {
            throw new IllegalArgumentException("logo must be a PNG, JPEG or WebP image");
        }
        bytes = bytes.clone();
    }

    public static DfLogo of(String dfId, String contentType, byte[] bytes) {
        return new DfLogo(dfId, contentType, bytes, Instant.now());
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }

    private static boolean hasSignatureOf(String contentType, byte[] bytes) {
        if (contentType == null) {
            return false;
        }
        if ("image/webp".equals(contentType)) {
            // RIFF container: "RIFF", four length bytes, then "WEBP"
            return bytes.length >= 12
                    && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                    && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
        }
        byte[] signature = SIGNATURES.get(contentType);
        return signature != null && bytes.length >= signature.length
                && Arrays.equals(bytes, 0, signature.length, signature, 0, signature.length);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof DfLogo that && dfId.equals(that.dfId) && contentType.equals(that.contentType)
                && Arrays.equals(bytes, that.bytes) && updatedAt.equals(that.updatedAt);
    }

    @Override
    public int hashCode() {
        return 31 * dfId.hashCode() + Arrays.hashCode(bytes);
    }

    @Override
    public String toString() {
        return "DfLogo[dfId=" + dfId + ", contentType=" + contentType + ", " + bytes.length + " bytes]";
    }
}
