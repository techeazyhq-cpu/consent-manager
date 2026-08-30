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
