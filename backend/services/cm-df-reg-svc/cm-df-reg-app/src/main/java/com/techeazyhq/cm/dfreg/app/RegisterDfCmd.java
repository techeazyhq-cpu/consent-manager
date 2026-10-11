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

package com.techeazyhq.cm.dfreg.app;

import com.techeazyhq.cm.dfreg.dom.DfPrfl;

/**
 * @param prfl       the profile as entered, not yet validated: {@link RegisterDfCmdHandler} builds the {@link DfPrfl}
 * @param subPlnName the plan to subscribe the fiduciary to on activation; null or blank for FREE
 */
public record RegisterDfCmd(String name, String contactEmail, PrflFields prfl, String subPlnName) {

    public record PrflFields(String legalName, String contactPhone, String website, String registeredAddress,
                             String industry, String cin, String gstin, String dpoName, String dpoEmail) {

        public static final PrflFields NONE = new PrflFields(null, null, null, null, null, null, null, null, null);

        DfPrfl toPrfl() {
            return new DfPrfl(legalName, contactPhone, website, registeredAddress, industry, cin, gstin, dpoName,
                    dpoEmail);
        }
    }
}
