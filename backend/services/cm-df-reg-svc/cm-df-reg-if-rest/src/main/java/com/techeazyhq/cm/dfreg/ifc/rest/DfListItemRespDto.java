package com.techeazyhq.cm.dfreg.ifc.rest;

import java.time.Instant;

import com.techeazyhq.cm.dfreg.dom.Df;
import com.techeazyhq.cm.dfreg.dom.DfSts;

/** List-view shape for {@code GET /api/df-reg/dfs} — omits {@code cmDfApiClientSecret}, shown only once at activation. */
public record DfListItemRespDto(String id, String name, String contactEmail, DfSts sts, Instant registeredAt) {
    public static DfListItemRespDto of(Df df) {
        return new DfListItemRespDto(df.id(), df.name(), df.contactEmail(), df.sts(), df.registeredAt());
    }
}
