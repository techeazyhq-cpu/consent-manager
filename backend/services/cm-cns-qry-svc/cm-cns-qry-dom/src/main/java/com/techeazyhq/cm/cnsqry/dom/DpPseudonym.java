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

package com.techeazyhq.cm.cnsqry.dom;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * How a data principal appears to platform staff (DPDPA s.8(5), data minimisation): a short handle derived from their
 * id with SHA-256, the same for every request of theirs, so staff can tell requests of one person from another's
 * without learning who that person is. Their id is random (a UUID), so the handle can't be turned back into it.
 */
public final class DpPseudonym {

    private static final int HEX_CHARS = 10;

    private DpPseudonym() {
    }

    /** {@code DP-} and ten hex digits, or null for a request whose principal has not signed up yet. */
    public static String of(String dpId) {
        if (dpId == null) {
            return null;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(dpId.getBytes(StandardCharsets.UTF_8));
            return "DP-" + HexFormat.of().formatHex(digest).substring(0, HEX_CHARS).toUpperCase(java.util.Locale.ROOT);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
