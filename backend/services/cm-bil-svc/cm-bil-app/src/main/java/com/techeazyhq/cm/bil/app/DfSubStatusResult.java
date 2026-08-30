package com.techeazyhq.cm.bil.app;

import com.techeazyhq.cm.bil.dom.DfSubSts;

/** Public-facing counterpart to {@link SubEligResult}, adding the plan name for mf-cm-admin's DF table. */
public record DfSubStatusResult(String subPlnId, String subPlnName, DfSubSts dfSubSts, long curPeriodUsg, long reqPerMonthQta) {
}
