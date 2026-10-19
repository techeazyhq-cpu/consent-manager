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

package com.techeazyhq.cm.stf.app;

import java.util.Comparator;

import com.techeazyhq.cm.audkit.AuditTrail;
import com.techeazyhq.cm.stf.dom.StfAcct;
import com.techeazyhq.cm.stf.dom.StfDirectoryPort;
import com.techeazyhq.cm.stf.dom.StfSession;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * A member of staff's own Profile and Sign-in &amp; security pages: their account, where they are signed in, and
 * signing out everywhere. Sign-in is by one-time code (ADR-008), so there is no password to change.
 */
public class MyAcctHandler {

    private final StfDirectoryPort directory;
    private final AuditTrail audit;

    public MyAcctHandler(StfDirectoryPort directory) {
        this(directory, AuditTrail.NONE);
    }

    public MyAcctHandler(StfDirectoryPort directory, AuditTrail audit) {
        this.directory = directory;
        this.audit = audit;
    }

    public Mono<StfAcct> profile(StfCaller caller) {
        return directory.findStaff(caller.id())
                .switchIfEmpty(Mono.error(new DomRuleViolation(404, "staff-not-found", "You have no staff account")));
    }

    /** Most recently used first. */
    public Flux<StfSession> sessions(StfCaller caller) {
        return directory.sessionsOf(caller.id())
                .sort(Comparator.comparing(StfSession::lastAccessAt, Comparator.nullsLast(Comparator.reverseOrder())));
    }

    public Mono<Void> signOutEverywhere(StfCaller caller) {
        return directory.signOutEverywhere(caller.id())
                .then(audit.record("staff.signed-out-everywhere", null, caller.id()));
    }
}
