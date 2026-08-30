package com.techeazyhq.cm.cnscmd.dom;

import java.util.Set;

/** The set of {@link DCat} a {@link CnsArt} authorizes access to — external claim name {@code scope}. */
public record Scp(Set<DCat> dCats) {
    public Scp {
        if (dCats == null || dCats.isEmpty()) {
            throw new IllegalArgumentException("scope must contain at least one data category");
        }
        dCats = Set.copyOf(dCats);
    }

    public boolean covers(DCat dCat) {
        return dCats.contains(dCat);
    }
}
