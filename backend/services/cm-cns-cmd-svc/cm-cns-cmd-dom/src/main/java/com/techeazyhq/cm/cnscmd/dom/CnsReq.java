package com.techeazyhq.cm.cnscmd.dom;

import java.time.Instant;

import com.techeazyhq.cm.domkit.Agg;

/**
 * The consent-request Aggregate Root — the write-side representation of Stage 1 (Capture)
 * through Stage 2 (Purpose Scoping, decided) of the whitepaper's consent lifecycle (plan §1,
 * §7c). Once approved it produces a separate {@link CnsArt} aggregate; the two are always
 * created/mutated together within {@code cm-cns-cmd-app}'s command handlers, each within its
 * own consistency boundary.
 */
public final class CnsReq extends Agg<String> {

    private final String dfId;
    private final Prps prps;
    private final Scp scp;
    private final String ntcTitle;
    private final String ntcText;
    private final Instant createdAt;
    private DpRef dpRef;
    private CnsReqSts sts;

    CnsReq(String id, String dfId, DpRef dpRef, Prps prps, Scp scp, String ntcTitle, String ntcText,
           CnsReqSts sts, Instant createdAt) {
        super(id);
        this.dfId = dfId;
        this.dpRef = dpRef;
        this.prps = prps;
        this.scp = scp;
        this.ntcTitle = ntcTitle;
        this.ntcText = ntcText;
        this.sts = sts;
        this.createdAt = createdAt;
    }

    /** Package-private: only {@link CnsReqFactory} (same package) raises the creation event. */
    void markCreated() {
        raise(CnsReqCreatedEvt.of(id(), dfId, dpRef, prps, scp));
    }

    public String dfId() {
        return dfId;
    }

    public DpRef dpRef() {
        return dpRef;
    }

    public Prps prps() {
        return prps;
    }

    public Scp scp() {
        return scp;
    }

    public String ntcTitle() {
        return ntcTitle;
    }

    public String ntcText() {
        return ntcText;
    }

    public CnsReqSts sts() {
        return sts;
    }

    public Instant createdAt() {
        return createdAt;
    }

    /** Applied when {@code LinkDpCmdHandler} resolves this request's DP identity (plan §7a). */
    public void linkDp(String newDpId) {
        if (dpRef.isLinked()) {
            return; // idempotent: already linked, no-op (plan §7a)
        }
        this.dpRef = DpRef.linked(newDpId);
    }

    public void approve() {
        requireSts(CnsReqSts.PENDING, "approve");
        this.sts = CnsReqSts.GRANTED;
    }

    public void deny() {
        requireSts(CnsReqSts.PENDING, "deny");
        this.sts = CnsReqSts.DENIED;
        raise(CnsDeniedEvt.of(id(), dfId, dpRef));
    }

    public void markNotificationFailed() {
        requireSts(CnsReqSts.PENDING, "markNotificationFailed");
        this.sts = CnsReqSts.NOTIFICATION_FAILED;
    }

    private void requireSts(CnsReqSts required, String action) {
        if (sts != required) {
            throw new IllegalStateException(
                    "Cannot %s a CnsReq in status %s (requires %s)".formatted(action, sts, required));
        }
    }
}
