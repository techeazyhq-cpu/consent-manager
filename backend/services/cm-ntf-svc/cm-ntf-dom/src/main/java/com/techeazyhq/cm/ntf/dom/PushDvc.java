/*
 * Copyright 2026 Vasantha Kumar
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * @author Vasantha Kumar <vasantha.kumar@hotmail.com>
 */

package com.techeazyhq.cm.ntf.dom;

import java.time.Instant;

/**
 * A device a DP registered for push notifications: a browser running the DP portal or a phone running the DP app,
 * identified by its FCM token. A token belongs to one browser profile or app install, so it belongs to whichever DP
 * registered it last — a shared computer or phone stops notifying the previous DP once someone else signs in and
 * turns notifications on.
 */
public record PushDvc(String id, String dpId, String token, PushPltf platform, Instant registeredAt, Instant lastSeenAt) {

    /** FCM tokens are ~160 characters; anything far longer is not a token. */
    public static final int MAX_TOKEN_LENGTH = 512;

    /** Oldest devices beyond this are dropped, so a DP's abandoned browsers and phones don't pile up. */
    public static final int MAX_PER_DP = 10;
}
