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

import java.time.Instant;

import com.techeazyhq.cm.domkit.Spec;

/** Specification (plan §12): a {@link CnsArt} is currently valid iff ISSUED and not past its ValPrd. */
public class CnsArtValidPolicy implements Spec<CnsArt> {

    private final Instant now;

    public CnsArtValidPolicy(Instant now) {
        this.now = now;
    }

    @Override
    public SpecResult evaluate(CnsArt cnsArt) {
        if (cnsArt.sts() != CnsArtSts.ISSUED) {
            return SpecResult.violated("cns-art-not-issued");
        }
        if (cnsArt.valPrd().isExpiredAt(now)) {
            return SpecResult.violated("cns-art-expired");
        }
        return SpecResult.ok();
    }
}
