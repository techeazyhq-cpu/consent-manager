package com.techeazyhq.cm.ntf.dom;

/**
 * The channels this platform can notify a DP/DF over (plan §9a). App is the DP dashboard itself — no adapter needed.
 * Push goes to a device token; nothing registers DP devices yet, so no caller selects it in Phase 1.
 */
public enum NtfChnl {
    EMAIL,
    SMS,
    WHATSAPP,
    PUSH,
    APP
}
