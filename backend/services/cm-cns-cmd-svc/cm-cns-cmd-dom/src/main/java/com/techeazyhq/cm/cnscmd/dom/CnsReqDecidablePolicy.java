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

import com.techeazyhq.cm.domkit.Spec;

/**
 * Specification (plan §12): a {@link CnsReq} may be approved/denied only while PENDING and
 * only once its DP identity is linked (plan §7c step 3 — the decision endpoint is only
 * reachable after §7a's auto-link, since an unlinked DP has no account to call it from).
 */
public class CnsReqDecidablePolicy implements Spec<CnsReq> {

    @Override
    public SpecResult evaluate(CnsReq cnsReq) {
        if (cnsReq.sts() != CnsReqSts.PENDING) {
            return SpecResult.violated("cns-req-not-pending");
        }
        if (!cnsReq.dpRef().isLinked()) {
            return SpecResult.violated("cns-req-dp-not-linked");
        }
        return SpecResult.ok();
    }
}
