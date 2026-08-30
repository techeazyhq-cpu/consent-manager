package com.techeazyhq.cm.ntf.app;

/**
 * Consumed from cm-cns-cmd-svc's {@code cm.ntf.cmd.notify-dp.v1} topic (plan §7c step 2, §9a).
 * {@code channelHint} is {@code EMAIL}/{@code PHONE} for an unlinked DP, or {@code APP} for a
 * linked one (whose dashboard already reflects the new request — no adapter dispatch needed).
 */
public record NotifyDpCmd(String cnsReqId, String dfId, String channelHint, String destination,
                           String ntcTitle, String ntcText) {
}
