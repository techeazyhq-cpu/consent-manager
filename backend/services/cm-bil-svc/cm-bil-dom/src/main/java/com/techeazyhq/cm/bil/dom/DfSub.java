package com.techeazyhq.cm.bil.dom;

import java.time.Instant;

import com.techeazyhq.cm.domkit.Agg;

/** A DF's active subscription (plan §11) — id is the {@code dfId} itself (one-to-one). */
public final class DfSub extends Agg<String> {

    private String subPlnId;
    private DfSubSts sts;
    private final Instant curPeriodFrom;
    private Instant curPeriodTo;

    DfSub(String dfId, String subPlnId, DfSubSts sts, Instant curPeriodFrom, Instant curPeriodTo) {
        super(dfId);
        this.subPlnId = subPlnId;
        this.sts = sts;
        this.curPeriodFrom = curPeriodFrom;
        this.curPeriodTo = curPeriodTo;
    }

    public String subPlnId() {
        return subPlnId;
    }

    public DfSubSts sts() {
        return sts;
    }

    public Instant curPeriodFrom() {
        return curPeriodFrom;
    }

    public Instant curPeriodTo() {
        return curPeriodTo;
    }

    public void assignPlan(String newSubPlnId) {
        this.subPlnId = newSubPlnId;
    }

    public void suspend() {
        this.sts = DfSubSts.SUSPENDED;
    }

    public void reactivate() {
        this.sts = DfSubSts.ACTIVE;
    }
}
