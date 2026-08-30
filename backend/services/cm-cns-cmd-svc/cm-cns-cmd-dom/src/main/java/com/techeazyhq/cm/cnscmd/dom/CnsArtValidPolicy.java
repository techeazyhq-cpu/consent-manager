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
