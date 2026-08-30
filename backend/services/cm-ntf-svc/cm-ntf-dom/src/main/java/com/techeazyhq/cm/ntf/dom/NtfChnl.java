package com.techeazyhq.cm.ntf.dom;

/** The channels this platform can notify a DP/DF over (plan §9a). App is the DP dashboard itself — no adapter needed. */
public enum NtfChnl {
    EMAIL,
    SMS,
    WHATSAPP,
    APP
}
