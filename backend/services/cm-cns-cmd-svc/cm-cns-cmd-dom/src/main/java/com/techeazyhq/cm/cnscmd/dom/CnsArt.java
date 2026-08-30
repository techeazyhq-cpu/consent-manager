package com.techeazyhq.cm.cnscmd.dom;

import java.time.Instant;

import com.techeazyhq.cm.domkit.Agg;

/**
 * The consent-artefact Aggregate Root — the data-blind, signed record of what a Data
 * Principal actually authorized (plan §1). Issued when a {@link CnsReq} is approved;
 * carries the {@code CnsTkn} a Data Fiduciary can validate offline (plan §7c, §9a).
 */
public final class CnsArt extends Agg<String> {

    private final String cnsReqId;
    private final String dfId;
    private final Prps prps;
    private final Scp scp;
    private final ValPrd valPrd;
    private final Instant issuedAt;
    private DpRef dpRef;
    private CnsArtSts sts;
    private String cnsTkn;

    CnsArt(String id, String cnsReqId, String dfId, DpRef dpRef, Prps prps, Scp scp, ValPrd valPrd,
           CnsArtSts sts, Instant issuedAt, String cnsTkn) {
        super(id);
        this.cnsReqId = cnsReqId;
        this.dfId = dfId;
        this.dpRef = dpRef;
        this.prps = prps;
        this.scp = scp;
        this.valPrd = valPrd;
        this.sts = sts;
        this.issuedAt = issuedAt;
        this.cnsTkn = cnsTkn;
    }

    public String cnsReqId() {
        return cnsReqId;
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

    public ValPrd valPrd() {
        return valPrd;
    }

    public CnsArtSts sts() {
        return sts;
    }

    public Instant issuedAt() {
        return issuedAt;
    }

    public String cnsTkn() {
        return cnsTkn;
    }

    /** The token can only be signed once the aggregate (and thus its id) exists — set by the CmdHandler after CnsTknSigner.sign(...). */
    public void attachSignedTkn(String cnsTkn) {
        if (this.cnsTkn != null) {
            throw new IllegalStateException("CnsTkn already attached to CnsArt " + id());
        }
        this.cnsTkn = cnsTkn;
    }

    /** Applied when {@code LinkDpCmdHandler} resolves this artefact's DP identity (plan §7a). */
    public void linkDp(String newDpId) {
        if (dpRef.isLinked()) {
            return; // idempotent, plan §7a
        }
        this.dpRef = DpRef.linked(newDpId);
    }

    public void revoke() {
        requireSts(CnsArtSts.ISSUED, "revoke");
        this.sts = CnsArtSts.REVOKED;
        raise(CnsRevokedEvt.of(id(), dpRef, dfId, prps));
    }

    /** Applied by the scheduled {@code CnsExpirySweepJob} (plan §8) for ValPrd-lapsed artefacts. */
    public void expire(Instant now) {
        requireSts(CnsArtSts.ISSUED, "expire");
        if (!valPrd.isExpiredAt(now)) {
            throw new IllegalStateException("CnsArt " + id() + " is not yet past its ValPrd");
        }
        this.sts = CnsArtSts.EXPIRED;
        raise(CnsExpiredEvt.of(id()));
    }

    private void requireSts(CnsArtSts required, String action) {
        if (sts != required) {
            throw new IllegalStateException(
                    "Cannot %s a CnsArt in status %s (requires %s)".formatted(action, sts, required));
        }
    }
}
