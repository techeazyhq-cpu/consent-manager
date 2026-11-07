package com.techeazyhq.cm.ntf.app;

/**
 * Consumed from {@code cm/ntf/cmd-notify-email-v1} (ADR-060): an email another service wants sent to an address it
 * holds, such as cm-df-reg-svc telling a fiduciary its usage is near its allowance. {@code ref} identifies the message,
 * so the same one sent twice is the same in notification-service's eyes.
 */
public record NotifyEmailCmd(String destination, String subject, String body, String ref) {
}
