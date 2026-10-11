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

import com.techeazyhq.cm.dfreg.app.RegisterDfCmd;

/** {@code POST /api/df-reg/dfs}: only {@code name} and {@code contactEmail} are required. */
public record DfRegisterReqDto(
        String name,
        String contactEmail,
        String subPlnName,
        String legalName,
        String contactPhone,
        String website,
        String registeredAddress,
        String industry,
        String cin,
        String gstin,
        String dpoName,
        String dpoEmail) {

    RegisterDfCmd toCmd() {
        return new RegisterDfCmd(name, contactEmail, new RegisterDfCmd.PrflFields(legalName, contactPhone, website,
                registeredAddress, industry, cin, gstin, dpoName, dpoEmail), subPlnName);
    }
}
