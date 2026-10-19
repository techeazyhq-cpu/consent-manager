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

package com.techeazyhq.cm.cnscmd.ifc.rest;

import java.util.Comparator;
import java.util.List;

import com.techeazyhq.cm.cnscmd.app.CnsReqTerms;
import com.techeazyhq.cm.cnscmd.dom.CnsGrant;
import com.techeazyhq.cm.cnscmd.dom.DCat;
import com.techeazyhq.cm.cnscmd.dom.PrpsDCat;
import com.techeazyhq.cm.cnscmd.dom.PrpsSensitivity;
import com.techeazyhq.cm.cnscmd.dom.PrpsVerContent;

/**
 * What a request asks, as the decision screen shows it (ADR-026, ADR-028): each category asked for, with whether
 * the fiduciary needs it and what happens without it; the validity requested and the longest allowed; and who else
 * receives the data. Only the categories the request asked for are listed, not the rest of the purpose's.
 */
record CnsReqTermsDto(String cnsReqId, String prpsCode, Integer prpsVer, String name, String description,
                      List<DCatDto> dCats, int requestedValidityDays, int maxValidityDays, Integer retentionDays,
                      List<String> recipients, List<String> transfers, List<String> flags, String ntcLang,
                      String ntcTitle, String ntcText, String ntcHash) {

    record DCatDto(String code, String dpv, String sensitivity, boolean necessary, String consequence) {
    }

    static CnsReqTermsDto of(CnsReqTerms.Terms t) {
        var req = t.req();
        PrpsVerContent v = t.version();
        List<DCatDto> dCats = req.scp().dCats().stream()
                .map(DCat::code)
                .sorted()
                .map(code -> {
                    PrpsDCat d = v == null ? null : v.dCat(code).orElse(null);
                    return d == null ? new DCatDto(code, null, PrpsSensitivity.ORDINARY.name(), false, null)
                            : new DCatDto(code, d.dpv(), d.sensitivity().name(), d.necessary(), d.consequence());
                })
                // needed data first, so the person reads what refusing it means before the optional rest
                .sorted(Comparator.comparing((DCatDto d) -> !d.necessary()))
                .toList();
        var ver = req.prpsVer();
        return new CnsReqTermsDto(req.id(), req.prps().code(), ver == null ? null : ver.verNo(),
                v == null ? null : v.name(), v == null ? null : v.description(), dCats,
                v == null ? CnsGrant.LEGACY_VALIDITY_DAYS : v.requestedValidityDays(),
                v == null ? CnsGrant.LEGACY_VALIDITY_DAYS : v.maxValidityDays(),
                v == null ? null : v.retentionDays(),
                v == null ? List.of() : v.recipients(), v == null ? List.of() : v.transfers(),
                v == null ? List.of() : v.flags().stream().map(Enum::name).sorted().toList(),
                ver == null ? null : ver.ntcLang(), req.ntcTitle(), req.ntcText(), ver == null ? null : ver.ntcHash());
    }
}
