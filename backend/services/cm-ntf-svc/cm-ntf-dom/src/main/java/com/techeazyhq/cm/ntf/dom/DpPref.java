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
 * How a principal wants to hear about new consent requests (ADR-066). A request is always on their dashboard and a sign-in code
 * is always sent: neither can be turned off. What can be:
 * <ul>
 *   <li>{@code pushEnabled}: push to their devices (on until they pause it, without removing each device);</li>
 *   <li>{@code emailRequests}: organisations' emails of a request to their address (on until they turn it off). Turned off, a
 *   request addressed to that email is not mailed, and waits on the dashboard.</li>
 * </ul>
 * The address is never kept: {@code emailHash} is a keyed hash of it, enough to recognise the address a request is sent to.
 */
public record DpPref(String dpId, boolean pushEnabled, boolean emailRequests, String emailHash, Instant updatedAt) {

    /** What a principal who never chose has: both on. */
    public static DpPref defaults(String dpId) {
        return new DpPref(dpId, true, true, null, null);
    }
}
