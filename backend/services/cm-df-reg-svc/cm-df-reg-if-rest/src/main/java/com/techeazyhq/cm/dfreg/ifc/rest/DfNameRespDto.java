package com.techeazyhq.cm.dfreg.ifc.rest;

import com.techeazyhq.cm.dfreg.dom.Df;

/** {@code GET /api/df-reg/dfs/{id}} response — name-only, safe for any authenticated role. */
public record DfNameRespDto(String id, String name) {
    public static DfNameRespDto of(Df df) {
        return new DfNameRespDto(df.id(), df.name());
    }
}
