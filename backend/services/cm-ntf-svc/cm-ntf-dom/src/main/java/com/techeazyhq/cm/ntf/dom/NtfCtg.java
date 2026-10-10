package com.techeazyhq.cm.ntf.dom;

/**
 * What a notification is for, which decides how urgently it is delivered: an {@code OTP} goes ahead of other
 * traffic and is dropped rather than sent after it expires; everything else this platform sends is {@code TRANSACTIONAL}.
 */
public enum NtfCtg {
    OTP,
    TRANSACTIONAL
}
