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

package com.techeazyhq.cm.cnscmd.dom;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * A purpose version's notice in one language (ISO/IEC 29184; DPDPA s.5 and the Eighth Schedule). Immutable once its
 * version is published, and identified by its {@link #hash()}, which every request and record made from it keeps.
 *
 * @param lang  a BCP 47 language tag, such as {@code en} or {@code hi-IN}
 * @param title what the principal sees first
 * @param text  the notice itself, as the fiduciary wrote it
 */
public record PrpsNtc(String lang, String title, String text) {

    /**
     * SHA-256, in hex, of the title, a blank line and the text, in UTF-8. The V3 migration computes the same for
     * requests sent before notices were versioned, so the formula must not change.
     */
    public String hash() {
        return hash(title, text);
    }

    public static String hash(String title, String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((title + "\n\n" + (text == null ? "" : text)).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
