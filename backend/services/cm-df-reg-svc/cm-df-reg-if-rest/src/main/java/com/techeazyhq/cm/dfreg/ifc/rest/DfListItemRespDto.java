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

package com.techeazyhq.cm.dfreg.ifc.rest;

import java.time.Instant;

import com.techeazyhq.cm.dfreg.dom.Df;
import com.techeazyhq.cm.dfreg.dom.DfSts;

/**
 * List-view shape for {@code GET /api/df-reg/dfs}, one dashboard tile per fiduciary — omits {@code cmDfApiClientSecret},
 * shown only once at activation. The logo is fetched separately, from {@code /api/df-reg/dfs/{id}/logo}.
 */
public record DfListItemRespDto(String id, String name, String contactEmail, DfSts sts, Instant registeredAt,
                                String legalName, String industry, String subPlnName) {

    public static DfListItemRespDto of(Df df) {
        return new DfListItemRespDto(df.id(), df.name(), df.contactEmail(), df.sts(), df.registeredAt(),
                df.prfl().legalName(), df.prfl().industry(), df.subPlnName());
    }
}
