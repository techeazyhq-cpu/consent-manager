package com.techeazyhq.cm.ntf.dom;

/**
 * A delivery the channel refused for good (bad recipient, channel not allowed, out of credit): sending the same
 * message again cannot succeed, so callers report it instead of retrying.
 */
public class NtfDeliveryRejectedException extends RuntimeException {

    private final String code;

    public NtfDeliveryRejectedException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
