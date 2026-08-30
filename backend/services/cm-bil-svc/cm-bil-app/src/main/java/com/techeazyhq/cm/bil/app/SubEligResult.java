package com.techeazyhq.cm.bil.app;

import com.techeazyhq.cm.bil.dom.DfSubSts;

public record SubEligResult(DfSubSts dfSubSts, long curPeriodUsg, long reqPerMonthQta, long reqPerSecQta) {
}
