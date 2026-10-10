package com.techeazyhq.cm.ntf.dom;

/**
 * A single outbound notification (plan §9a) — one per channel a `NotifyDpCmd`/`NotifyDfCmd` fans out to, or an OTP code.
 * {@code ref} is a stable id of what triggered the message (e.g. {@code cm-notify-dp-<cnsReqId>}): adapters that call
 * an external delivery service use it as the idempotency key, so a redelivered command is never sent twice.
 */
public record NtfMsg(String destination, String subject, String body, NtfCtg category, String ref) {

    public NtfMsg {
        if (category == null) {
            category = NtfCtg.TRANSACTIONAL;
        }
    }

    public NtfMsg(String destination, String subject, String body) {
        this(destination, subject, body, NtfCtg.TRANSACTIONAL, null);
    }
}
