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

import org.springframework.r2dbc.core.DatabaseClient;

import com.techeazyhq.cm.cnscmd.dom.PrpsVerRef;

import io.r2dbc.spi.Readable;

/** The purpose version and notice a request or batch names (ADR-031), as {@code cns_req} and {@code cns_req_batch} hold them. */
final class PrpsVerColumns {

    static final String NAMES = "prps_ver_id, prps_ver_no, ntc_lang, ntc_hash";
    static final String PARAMS = ":prpsVerId, :prpsVerNo, :ntcLang, :ntcHash";

    private PrpsVerColumns() {
    }

    static DatabaseClient.GenericExecuteSpec bind(DatabaseClient.GenericExecuteSpec spec, PrpsVerRef ref) {
        return spec.bind("prpsVerId", ref.prpsVerId())
                .bind("prpsVerNo", ref.verNo())
                .bind("ntcLang", ref.ntcLang())
                .bind("ntcHash", ref.ntcHash());
    }

    static PrpsVerRef read(Readable row) {
        String id = row.get("prps_ver_id", String.class);
        if (id == null) {
            return null;
        }
        Integer verNo = row.get("prps_ver_no", Integer.class);
        return new PrpsVerRef(id, verNo == null ? 0 : verNo, row.get("ntc_lang", String.class),
                row.get("ntc_hash", String.class));
    }
}
