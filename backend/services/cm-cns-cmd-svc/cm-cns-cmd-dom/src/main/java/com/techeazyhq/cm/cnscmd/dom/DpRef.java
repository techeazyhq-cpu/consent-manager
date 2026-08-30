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

    /** True if this ref would be resolved by linking the given external contact info (plan §7a). */
    default boolean matches(ExtDpRefTyp typ, String val) {
        return this instanceof UnlnkDpRef unlnk
                && unlnk.typ() == typ
                && unlnk.val().equals(typ == ExtDpRefTyp.EMAIL ? val.trim().toLowerCase(Locale.ROOT) : val.trim());
    }
}
