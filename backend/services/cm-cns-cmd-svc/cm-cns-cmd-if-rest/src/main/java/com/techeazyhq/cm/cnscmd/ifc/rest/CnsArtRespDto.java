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

import com.techeazyhq.cm.cnscmd.dom.CnsArt;
import com.techeazyhq.cm.cnscmd.dom.CnsArtSts;

public record CnsArtRespDto(String id, String cnsReqId, String dfId, CnsArtSts sts, String cnsTkn) {
    public static CnsArtRespDto of(CnsArt cnsArt) {
        return new CnsArtRespDto(cnsArt.id(), cnsArt.cnsReqId(), cnsArt.dfId(), cnsArt.sts(), cnsArt.cnsTkn());
    }
}
