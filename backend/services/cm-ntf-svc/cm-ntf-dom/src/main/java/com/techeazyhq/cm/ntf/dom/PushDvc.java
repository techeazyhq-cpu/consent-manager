package com.techeazyhq.cm.ntf.dom;

import java.time.Instant;

/**
 * A device a DP registered for push notifications: today a browser running the DP portal, identified by its
 * web-push token. A token belongs to one browser profile, so it belongs to whichever DP registered it last — a
 * shared computer stops notifying the previous DP once someone else signs in and turns notifications on.
 */
public record PushDvc(String id, String dpId, String token, PushPltf platform, Instant registeredAt, Instant lastSeenAt) {

    /** FCM tokens are ~160 characters; anything far longer is not a token. */
    public static final int MAX_TOKEN_LENGTH = 512;

    /** Oldest devices beyond this are dropped, so a DP's abandoned browsers don't pile up. */
    public static final int MAX_PER_DP = 10;
}
