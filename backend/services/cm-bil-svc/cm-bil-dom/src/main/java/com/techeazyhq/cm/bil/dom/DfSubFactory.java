package com.techeazyhq.cm.bil.dom;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public final class DfSubFactory {

    private DfSubFactory() {
    }

    /** Auto-created on {@code DfActivatedEvt} (plan §7c, §11) — always starts on the FREE plan. */
    public static DfSub createFree(String dfId, String freeSubPlnId) {
        Instant now = Instant.now();
        return new DfSub(dfId, freeSubPlnId, DfSubSts.ACTIVE, now, now.plus(30, ChronoUnit.DAYS));
    }

    public static DfSub rehydrate(String dfId, String subPlnId, DfSubSts sts, Instant curPeriodFrom, Instant curPeriodTo) {
        return new DfSub(dfId, subPlnId, sts, curPeriodFrom, curPeriodTo);
    }
}
