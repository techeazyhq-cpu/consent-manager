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
