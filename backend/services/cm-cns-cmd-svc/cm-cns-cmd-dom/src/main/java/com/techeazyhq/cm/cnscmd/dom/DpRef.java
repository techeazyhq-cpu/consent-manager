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

import java.util.Locale;
import java.util.Objects;

/**
 * Deferred Data Principal identity linking (plan §7a): a {@link CnsReq}/{@link CnsArt} can be
 * raised against a DP who has no CM identity yet. Sealed so "identity not yet resolved" is a
 * compile-time-checked domain state instead of a nullable {@code dpId} plus a boolean flag
 * (Sealed Value Object pattern, plan §12).
 */
public sealed interface DpRef {

    record LnkdDpRef(String dpId) implements DpRef {
        public LnkdDpRef {
            Objects.requireNonNull(dpId, "dpId must not be null");
            if (dpId.isBlank()) {
                throw new IllegalArgumentException("dpId must not be blank");
            }
        }
    }

    record UnlnkDpRef(ExtDpRefTyp typ, String val) implements DpRef {
        public UnlnkDpRef {
            Objects.requireNonNull(typ, "typ must not be null");
            Objects.requireNonNull(val, "val must not be null");
            val = normalize(typ, val);
        }

        private static String normalize(ExtDpRefTyp typ, String raw) {
            String trimmed = raw.trim();
            if (trimmed.isEmpty()) {
                throw new IllegalArgumentException("val must not be blank");
            }
            return typ == ExtDpRefTyp.EMAIL ? trimmed.toLowerCase(Locale.ROOT) : trimmed;
        }
    }

    static DpRef linked(String dpId) {
        return new LnkdDpRef(dpId);
    }

    static DpRef unlinked(ExtDpRefTyp typ, String val) {
        return new UnlnkDpRef(typ, val);
    }

    default boolean isLinked() {
        return this instanceof LnkdDpRef;
    }

    /** Whether this is the linked principal {@code dpId}: only they decide on, or withdraw, their consent. */
    default boolean isOf(String dpId) {
        return dpId != null && this instanceof LnkdDpRef linked && linked.dpId().equals(dpId);
    }

    /** True if this ref would be resolved by linking the given external contact info (plan §7a). */
    default boolean matches(ExtDpRefTyp typ, String val) {
        return this instanceof UnlnkDpRef unlnk
                && unlnk.typ() == typ
                && unlnk.val().equals(typ == ExtDpRefTyp.EMAIL ? val.trim().toLowerCase(Locale.ROOT) : val.trim());
    }
}
