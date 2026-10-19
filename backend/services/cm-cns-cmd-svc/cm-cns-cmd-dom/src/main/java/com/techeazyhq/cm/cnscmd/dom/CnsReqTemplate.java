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

package com.techeazyhq.cm.cnscmd.dom;

/**
 * What a request asks (ADR-031, ADR-036): a published purpose version, the data categories asked for, and the notice
 * the principal is shown, in one language. Made by {@link PrpsDef#templateFor}; a batch asks every recipient the same.
 *
 * <p>The constructor takes values as they are, as stored requests and batches are read back with it; a new template
 * is made with {@link #of}, which checks them. A stored batch that no longer passes today's checks must still be
 * readable (VAPT: one that couldn't be made every listing of its fiduciary's batches fail).
 *
 * @param prpsVer the purpose version and notice; null only for a request stored before purposes were versioned and
 *                never migrated
 */
public record CnsReqTemplate(Prps prps, Scp scp, String ntcTitle, String ntcText, PrpsVerRef prpsVer) {

    static CnsReqTemplate of(Prps prps, Scp scp, String ntcTitle, String ntcText, PrpsVerRef prpsVer) {
        if (ntcTitle == null || ntcTitle.isBlank()) {
            throw new IllegalArgumentException("notice title must not be blank");
        }
        if (ntcText == null || ntcText.isBlank()) {
            throw new IllegalArgumentException("notice text must not be blank: people decide on it");
        }
        if (prpsVer == null) {
            throw new IllegalArgumentException("a request names a published purpose version");
        }
        CnsReqFactory.checkInput(prps, scp, ntcTitle, ntcText);
        return new CnsReqTemplate(prps, scp, ntcTitle, ntcText, prpsVer);
    }

    /** This template's request for one person. */
    public CnsReq requestFor(String dfId, Recipient recipient) {
        return CnsReqFactory.create(dfId, recipient.dpRef(), this);
    }
}
