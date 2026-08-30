package com.techeazyhq.cm.cnscmd.infra;

import com.techeazyhq.cm.cnscmd.dom.DpRef;
import com.techeazyhq.cm.cnscmd.dom.ExtDpRefTyp;
import io.r2dbc.spi.Row;

/**
 * Maps {@link DpRef} to/from the {@code dp_ref_typ}/{@code dp_ref_val}/{@code dp_id} triplet
 * every {@code cns_req}/{@code cns_art} row carries (plan §8: "dp_ref_typ, dp_ref_val columns
 * modeling DpRef"). Shared by both R2DBC adapters to keep the mapping in one place.
 */
final class DpRefColumns {

    private DpRefColumns() {
    }

    static String typOf(DpRef ref) {
        return ref instanceof DpRef.UnlnkDpRef unlnk ? unlnk.typ().name() : null;
    }

    static String valOf(DpRef ref) {
        return ref instanceof DpRef.UnlnkDpRef unlnk ? unlnk.val() : null;
    }

    static String dpIdOf(DpRef ref) {
        return ref instanceof DpRef.LnkdDpRef lnkd ? lnkd.dpId() : null;
    }

    static DpRef fromRow(Row row) {
        String dpId = row.get("dp_id", String.class);
        if (dpId != null) {
            return DpRef.linked(dpId);
        }
        String typ = row.get("dp_ref_typ", String.class);
        String val = row.get("dp_ref_val", String.class);
        return DpRef.unlinked(ExtDpRefTyp.valueOf(typ), val);
    }
}
