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
import com.techeazyhq.cm.dfreg.dom.DfPrfl;
import com.techeazyhq.cm.dfreg.dom.DfSts;

/**
 * {@code GET /api/df-reg/dfs/{id}/profile}, for platform staff only (the gateway enforces it): everything known about a
 * fiduciary except its API client secret, which is shown once, at activation.
 */
public record DfProfileRespDto(
        String id,
        String name,
        String contactEmail,
        DfSts sts,
        String subPlnName,
        Instant registeredAt,
        Instant activatedAt,
        String legalName,
        String contactPhone,
        String website,
        String registeredAddress,
        String industry,
        String cin,
        String gstin,
        String dpoName,
        String dpoEmail) {

    public static DfProfileRespDto of(Df df) {
        DfPrfl prfl = df.prfl();
        return new DfProfileRespDto(df.id(), df.name(), df.contactEmail(), df.sts(), df.subPlnName(),
                df.registeredAt(), df.activatedAt(), prfl.legalName(), prfl.contactPhone(), prfl.website(),
                prfl.registeredAddress(), prfl.industry(), prfl.cin(), prfl.gstin(), prfl.dpoName(), prfl.dpoEmail());
    }
}
