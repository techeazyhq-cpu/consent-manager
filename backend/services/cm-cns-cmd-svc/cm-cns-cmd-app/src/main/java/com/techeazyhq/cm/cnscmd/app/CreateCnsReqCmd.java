package com.techeazyhq.cm.cnscmd.app;

import java.util.Set;

import com.techeazyhq.cm.cnscmd.dom.ExtDpRefTyp;

/**
 * Either {@code dpId} (DP already has a CM identity) or {@code extDpRefTyp}/{@code extDpRefVal}
 * (DP not yet registered, plan §7a) must be supplied — never both, never neither; validated by
 * {@code CreateCnsReqCmdHandler}.
 */
public record CreateCnsReqCmd(
        String dfId,
        String dpId,
        ExtDpRefTyp extDpRefTyp,
        String extDpRefVal,
        String prpsCode,
        Set<String> dCatCodes,
        String ntcTitle,
        String ntcText) {
}
