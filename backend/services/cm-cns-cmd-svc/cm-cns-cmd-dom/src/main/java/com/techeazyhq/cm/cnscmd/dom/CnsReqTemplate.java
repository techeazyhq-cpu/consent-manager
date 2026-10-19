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

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * What a batch asks every recipient (ADR-036): one purpose, one set of data, one notice. Each recipient still gets a
 * request of their own, made from this.
 *
 * <p>The constructor takes values as they are, as stored batches are read back with it; a new template is made with
 * {@link #of}, which checks them. A stored batch that no longer passes today's checks must still be readable (VAPT:
 * one that couldn't be made every listing of its fiduciary's batches fail).
 */
public record CnsReqTemplate(Prps prps, Scp scp, String ntcTitle, String ntcText) {

    public static CnsReqTemplate of(String prpsCode, Collection<String> dCatCodes, String ntcTitle, String ntcText) {
        if (prpsCode == null) {
            throw new IllegalArgumentException("purpose code must not be blank");
        }
        Scp scp = new Scp(dCatCodes == null ? java.util.Set.of()
                : dCatCodes.stream().filter(c -> c != null && !c.isBlank()).map(DCat::new).collect(Collectors.toSet()));
        // normalised before it is checked, so what is checked is what is stored
        String title = ntcTitle == null ? null : ntcTitle.strip();
        String text = ntcText == null ? null : ntcText.strip();
        if (title == null || title.isEmpty()) {
            throw new IllegalArgumentException("notice title must not be blank");
        }
        if (text == null || text.isEmpty()) {
            throw new IllegalArgumentException("notice text must not be blank: people decide on it");
        }
        Prps prps = new Prps(prpsCode.strip());
        CnsReqFactory.checkInput(prps, scp, title, text);
        return new CnsReqTemplate(prps, scp, title, text);
    }

    /** This template's request for one person. */
    public CnsReq requestFor(String dfId, Recipient recipient) {
        return CnsReqFactory.create(dfId, recipient.dpRef(), prps, scp, ntcTitle, ntcText);
    }
}
