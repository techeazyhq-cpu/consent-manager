package com.techeazyhq.cm.ntf.dom;

/** A single outbound notification (plan §9a) — one per channel a `NotifyDpCmd`/`NotifyDfCmd` fans out to, or an OTP code. */
public record NtfMsg(String destination, String subject, String body) {
}
