package com.techeazyhq.cm.cnscmd.dom;

import java.util.Objects;

/** Processing purpose (Stage 2: Purpose Scoping, plan §1) — external claim name {@code purpose_code}. */
public record Prps(String code) {
    public Prps {
        Objects.requireNonNull(code, "code must not be null");
        if (code.isBlank()) {
            throw new IllegalArgumentException("purpose code must not be blank");
        }
    }
}
