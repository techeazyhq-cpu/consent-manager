package com.techeazyhq.cm.ntf.app;

/** Consumed from cm-cns-cmd-svc's {@code cm.ntf.cmd.notify-df.v1} topic (plan §7c steps 4/5). */
public record NotifyDfCmd(String dfId, String subjectId, String outcome) {
}
