package com.techeazyhq.cm.domkit;

/**
 * Specification pattern base (plan §12) for composable domain business rules, e.g.
 * {@code CnsReqDecidablePolicy}, {@code DfSubEligiblePolicy}, {@code CnsArtValidPolicy}.
 * Implementations return a reason on failure rather than a bare boolean, since every
 * rejection in this platform must map to a specific {@code problem+json} type.
 */
public interface Spec<T> {

    SpecResult evaluate(T candidate);

    record SpecResult(boolean satisfied, String reasonCode) {
        public static SpecResult ok() {
            return new SpecResult(true, null);
        }

        public static SpecResult violated(String reasonCode) {
            return new SpecResult(false, reasonCode);
        }
    }
}
