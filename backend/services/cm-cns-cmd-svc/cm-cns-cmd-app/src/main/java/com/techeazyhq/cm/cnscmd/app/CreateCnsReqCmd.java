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

package com.techeazyhq.cm.cnscmd.app;

import java.util.Set;

import com.techeazyhq.cm.cnscmd.dom.ExtDpRefTyp;

/**
 * Either {@code dpId} (DP already has a CM identity) or {@code extDpRefTyp}/{@code extDpRefVal}
 * (DP not yet registered, plan §7a) must be supplied — never both, never neither; validated by
 * {@code CreateCnsReqCmdHandler}. The request asks for published purpose version {@code prpsVer} (ADR-031), with its
 * notice in {@code ntcLang} (English or the first written when null), for {@code dCatCodes} (all of the version's
 * categories when empty).
 */
public record CreateCnsReqCmd(
        String dfId,
        String dpId,
        ExtDpRefTyp extDpRefTyp,
        String extDpRefVal,
        String prpsCode,
        Integer prpsVer,
        String ntcLang,
        Set<String> dCatCodes) {
}
