package com.techeazyhq.cm.cnscmd.app;

import com.techeazyhq.cm.cnscmd.dom.ExtDpRefTyp;

/**
 * {@code newDpId} always comes from the caller's own JWT {@code sub} at the REST layer (plan
 * §7a) — never client-supplied — so a DP can only ever link records matching their own
 * verified contact info.
 */
public record LinkDpCmd(String newDpId, ExtDpRefTyp extDpRefTyp, String extDpRefVal) {
}
