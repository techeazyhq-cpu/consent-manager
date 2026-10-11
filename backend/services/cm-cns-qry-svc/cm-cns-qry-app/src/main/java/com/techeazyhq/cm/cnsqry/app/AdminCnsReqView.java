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

package com.techeazyhq.cm.cnsqry.app;

import java.time.Instant;
import java.util.Set;

import com.techeazyhq.cm.cnsqry.dom.CnsArtRdMdl;
import com.techeazyhq.cm.cnsqry.dom.CnsLcySts;
import com.techeazyhq.cm.cnsqry.dom.DpPseudonym;

/**
 * A consent request as platform staff see it: what was asked, of whom only as a pseudonym ({@link DpPseudonym}), and
 * where it stands. {@code dpPseudonym} is null while the principal has not signed up; the email or phone number the
 * request was sent to never reaches this side.
 */
public record AdminCnsReqView(
        String cnsReqId,
        String cnsArtId,
        String dpPseudonym,
        String prpsCode,
        Set<String> dCatCodes,
        CnsLcySts lcySts,
        Instant validUntil,
        Instant createdAt,
        Instant updatedAt) {

    static AdminCnsReqView of(CnsArtRdMdl row) {
        return new AdminCnsReqView(row.cnsReqId(), row.cnsArtId(), DpPseudonym.of(row.dpId()), row.prpsCode(),
                row.dCatCodes(), row.lcySts(), row.validUntil(), row.createdAt(), row.updatedAt());
    }
}
