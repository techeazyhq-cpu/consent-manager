package com.techeazyhq.cm.cnscmd.dom;

import java.util.Objects;

/** A single authorized data category, e.g. {@code EMAIL}, {@code PURCHASE_HISTORY}. */
public record DCat(String code) {
    public DCat {
        Objects.requireNonNull(code, "code must not be null");
        if (code.isBlank()) {
            throw new IllegalArgumentException("data category code must not be blank");
        }
    }
}
