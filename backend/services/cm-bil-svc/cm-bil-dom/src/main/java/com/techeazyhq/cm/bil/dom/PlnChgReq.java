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

package com.techeazyhq.cm.bil.dom;

import java.time.Instant;
import java.util.UUID;

/**
 * A fiduciary asking the platform for another plan (ADR-039). Fiduciaries don't change their own plan, as there are no
 * payments to back a bigger one: they ask, and a platform administrator approves (the plan changes) or declines (with a
 * reason). The person who asked, or another Owner or Administrator, can withdraw it while it waits. A fiduciary has at
 * most one request waiting at a time.
 */
public final class PlnChgReq {

    public enum Sts {
        PENDING, APPROVED, DECLINED, WITHDRAWN
    }

    static final int MAX_NOTE = 500;

    private final String id;
    private final String dfId;
    private final String fromPlnName;
    private final String toPlnName;
    private final String note;
    private final String requestedBy;
    private final String requestedByName;
    private final Instant requestedAt;
    private Sts sts;
    private String decidedBy;
    private String decidedByName;
    private Instant decidedAt;
    private String decisionNote;

    private PlnChgReq(String id, String dfId, String fromPlnName, String toPlnName, String note, String requestedBy,
                      String requestedByName, Instant requestedAt, Sts sts, String decidedBy, String decidedByName,
                      Instant decidedAt, String decisionNote) {
        this.id = id;
        this.dfId = dfId;
        this.fromPlnName = fromPlnName;
        this.toPlnName = toPlnName;
        this.note = note;
        this.requestedBy = requestedBy;
        this.requestedByName = requestedByName;
        this.requestedAt = requestedAt;
        this.sts = sts;
        this.decidedBy = decidedBy;
        this.decidedByName = decidedByName;
        this.decidedAt = decidedAt;
        this.decisionNote = decisionNote;
    }

    /** @throws IllegalArgumentException for a request to the plan the fiduciary already has, or an overlong note */
    public static PlnChgReq request(String dfId, String fromPlnName, String toPlnName, String note, String requestedBy,
                                    String requestedByName) {
        if (toPlnName == null || toPlnName.isBlank()) {
            throw new IllegalArgumentException("Say which plan you'd like");
        }
        if (toPlnName.equals(fromPlnName)) {
            throw new IllegalArgumentException("You're already on " + toPlnName);
        }
        return new PlnChgReq(UUID.randomUUID().toString(), dfId, fromPlnName, toPlnName, clean(note), requestedBy,
                requestedByName, Instant.now(), Sts.PENDING, null, null, null, null);
    }

    public static PlnChgReq rehydrate(String id, String dfId, String fromPlnName, String toPlnName, String note,
                                      String requestedBy, String requestedByName, Instant requestedAt, Sts sts,
                                      String decidedBy, String decidedByName, Instant decidedAt, String decisionNote) {
        return new PlnChgReq(id, dfId, fromPlnName, toPlnName, note, requestedBy, requestedByName, requestedAt, sts,
                decidedBy, decidedByName, decidedAt, decisionNote);
    }

    public void approve(String by, String byName, String reason) {
        decide(Sts.APPROVED, by, byName, reason);
    }

    /** A decline says why, so the fiduciary knows what to do next. */
    public void decline(String by, String byName, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Say why the request is declined");
        }
        decide(Sts.DECLINED, by, byName, reason);
    }

    public void withdraw(String by, String byName) {
        decide(Sts.WITHDRAWN, by, byName, null);
    }

    private void decide(Sts outcome, String by, String byName, String reason) {
        if (sts != Sts.PENDING) {
            throw new IllegalStateException("This request was already " + sts.name().toLowerCase());
        }
        this.sts = outcome;
        this.decidedBy = by;
        this.decidedByName = byName;
        this.decidedAt = Instant.now();
        this.decisionNote = clean(reason);
    }

    private static String clean(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.length() > MAX_NOTE) {
            throw new IllegalArgumentException("Keep the note to " + MAX_NOTE + " characters");
        }
        return trimmed;
    }

    public String id() {
        return id;
    }

    public String dfId() {
        return dfId;
    }

    public String fromPlnName() {
        return fromPlnName;
    }

    public String toPlnName() {
        return toPlnName;
    }

    public String note() {
        return note;
    }

    public String requestedBy() {
        return requestedBy;
    }

    public String requestedByName() {
        return requestedByName;
    }

    public Instant requestedAt() {
        return requestedAt;
    }

    public Sts sts() {
        return sts;
    }

    public String decidedBy() {
        return decidedBy;
    }

    public String decidedByName() {
        return decidedByName;
    }

    public Instant decidedAt() {
        return decidedAt;
    }

    public String decisionNote() {
        return decisionNote;
    }
}
