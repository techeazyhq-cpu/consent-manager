package com.techeazyhq.cm.cnscmd.dom;

import java.time.Instant;

/** Validity period of a {@link CnsArt}; {@code until} maps to the external {@code exp} claim. */
public record ValPrd(Instant from, Instant until) {
    public ValPrd {
        if (from == null || until == null) {
            throw new IllegalArgumentException("from/until must not be null");
        }
        if (!until.isAfter(from)) {
            throw new IllegalArgumentException("until must be after from");
        }
    }

    public boolean isExpiredAt(Instant instant) {
        return instant.isAfter(until);
    }

    public static ValPrd of(Instant from, java.time.Duration duration) {
        return new ValPrd(from, from.plus(duration));
    }
}
