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


package com.techeazyhq.cm.ntf.app;

import java.time.Clock;

import com.techeazyhq.cm.ntf.dom.DpPref;
import com.techeazyhq.cm.ntf.dom.DpPrefRepoPort;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Mono;

/**
 * A principal's choices about how they hear of new consent requests (ADR-066). The email choice is tied to the address they
 * signed in with, which the sign-in code proved is theirs, kept only as a keyed hash; someone who signs in with a mobile number
 * has no address to tie it to, so has no email choice to make.
 */
public class DpPrefCmdHandler {

    private final DpPrefRepoPort prefs;
    private final EmailFingerprint fingerprint;
    private final Clock clock;

    public DpPrefCmdHandler(DpPrefRepoPort prefs, EmailFingerprint fingerprint, Clock clock) {
        this.prefs = prefs;
        this.fingerprint = fingerprint;
        this.clock = clock;
    }

    public Mono<DpPref> get(String dpId) {
        return prefs.find(dpId).defaultIfEmpty(DpPref.defaults(dpId));
    }

    /** @param signedInEmail the address in the caller's verified token, or null when they signed in with a mobile number */
    public Mono<DpPref> set(String dpId, boolean push, boolean emailRequests, String signedInEmail) {
        boolean hasAddress = signedInEmail != null && !signedInEmail.isBlank();
        if (!emailRequests && !hasAddress) {
            return Mono.error(new DomRuleViolation(409, "email-not-available",
                    "You signed in with a mobile number, so there is no email address of yours to stop emails to"));
        }
        return prefs.save(new DpPref(dpId, push, emailRequests, hasAddress ? fingerprint.of(signedInEmail) : null, clock.instant()));
    }

    /** The address with all but its first letter hidden, for showing back: {@code a•••@example.com}. */
    public static String mask(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        int at = email.indexOf('@');
        return at < 1 ? "•••" : email.charAt(0) + "•••" + email.substring(at);
    }
}
