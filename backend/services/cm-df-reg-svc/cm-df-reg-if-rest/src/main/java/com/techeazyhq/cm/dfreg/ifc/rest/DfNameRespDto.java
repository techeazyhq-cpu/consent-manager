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

import com.techeazyhq.cm.dfreg.dom.Df;

/** {@code GET /api/df-reg/dfs/{id}} response — name-only, safe for any authenticated role. */
public record DfNameRespDto(String id, String name) {
    public static DfNameRespDto of(Df df) {
        return new DfNameRespDto(df.id(), df.name());
    }
}
