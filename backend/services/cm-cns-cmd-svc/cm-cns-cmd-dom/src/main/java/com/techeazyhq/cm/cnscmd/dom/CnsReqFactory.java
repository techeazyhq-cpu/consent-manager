package com.techeazyhq.cm.cnscmd.dom;

import java.time.Instant;
import java.util.UUID;

/** Factory Method (plan §12): a {@link CnsReq} can never be constructed in an invalid state. */
public final class CnsReqFactory {

    private CnsReqFactory() {
    }

    public static CnsReq create(String dfId, DpRef dpRef, Prps prps, Scp scp, String ntcTitle, String ntcText) {
        if (dfId == null || dfId.isBlank()) {
            throw new IllegalArgumentException("dfId must not be blank");
        }
        if (ntcTitle == null || ntcTitle.isBlank()) {
            throw new IllegalArgumentException("notice title must not be blank");
        }
        String id = UUID.randomUUID().toString();
        CnsReq cnsReq = new CnsReq(id, dfId, dpRef, prps, scp, ntcTitle, ntcText, CnsReqSts.PENDING, Instant.now());
        cnsReq.markCreated();
        return cnsReq;
    }

    /** Rehydration from persistence — no invariant re-checking, no event raised. */
    public static CnsReq rehydrate(String id, String dfId, DpRef dpRef, Prps prps, Scp scp, String ntcTitle,
                                    String ntcText, CnsReqSts sts, Instant createdAt) {
        return new CnsReq(id, dfId, dpRef, prps, scp, ntcTitle, ntcText, sts, createdAt);
    }
}
