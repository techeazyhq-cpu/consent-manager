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

import java.util.Set;

import com.techeazyhq.cm.cnscmd.dom.ExtDpRefTyp;

/**
 * Request body for {@code POST /api/cns-cmd/cns-reqs} — the DF Integration API's core write operation (plan §10). It
 * names a published purpose version (ADR-031): {@code prpsCode} and {@code prpsVer}; optionally the notice's language
 * {@code ntcLang} and the data categories {@code dCatCodes} (all the version's, when none are named).
 */
public record CnsReqCreateReqDto(
        String dpId,
        ExtDpRefTyp extDpRefTyp,
        String extDpRefVal,
        String prpsCode,
        Integer prpsVer,
        String ntcLang,
        Set<String> dCatCodes) {
}
