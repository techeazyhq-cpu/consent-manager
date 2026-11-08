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


package com.techeazyhq.cm.dfreg.dom;

import java.time.Instant;
import java.util.UUID;

/**
 * A member of platform staff proposing to end a fiduciary's contract, or to call such an ending off, which a second
 * member must approve (ADR-064 D7): one person cannot do it alone, so a mistake or a bad actor is caught by the other.
 * The person who proposed cannot decide it. It is decided once.
 */
public final class LeaveProposal {

    public enum Kind { START, CANCEL }

    public enum Status { PENDING, APPROVED, REJECTED, WITHDRAWN }

    private final String id;
    private final String dfId;
    private final Kind kind;
    private final String reason;
    private final String proposedBy;
    private final Instant proposedAt;
    private Status status;
    private String decidedBy;
    private Instant decidedAt;
    private String decisionNote;

    LeaveProposal(String id, String dfId, Kind kind, String reason, String proposedBy, Instant proposedAt, Status status,
                  String decidedBy, Instant decidedAt, String decisionNote) {
        this.id = id;
        this.dfId = dfId;
        this.kind = kind;
        this.reason = reason;
        this.proposedBy = proposedBy;
        this.proposedAt = proposedAt;
        this.status = status;
        this.decidedBy = decidedBy;
        this.decidedAt = decidedAt;
        this.decisionNote = decisionNote;
    }

    public static LeaveProposal propose(String dfId, Kind kind, String reason, String proposedBy, Instant at) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A reason is needed");
        }
        if (reason.trim().length() > 500) {
            throw new IllegalArgumentException("The reason must be at most 500 characters");
        }
        return new LeaveProposal(UUID.randomUUID().toString(), dfId, kind, reason.trim(), proposedBy, at, Status.PENDING, null, null, null);
    }

    public static LeaveProposal rehydrate(String id, String dfId, Kind kind, String reason, String proposedBy, Instant proposedAt,
                                          Status status, String decidedBy, Instant decidedAt, String decisionNote) {
        return new LeaveProposal(id, dfId, kind, reason, proposedBy, proposedAt, status, decidedBy, decidedAt, decisionNote);
    }

    /** Thrown when the person deciding is the one who proposed. */
    public static final class SamePersonException extends IllegalStateException {
        SamePersonException() {
            super("A second member of staff must decide this: the person who proposed it cannot");
        }
    }

    public void approve(String by, Instant at) {
        pending();
        if (by == null || by.equals(proposedBy)) {
            throw new SamePersonException();
        }
        decide(Status.APPROVED, by, at, null);
    }

    public void reject(String by, Instant at, String note) {
        pending();
        if (by == null || by.equals(proposedBy)) {
            throw new SamePersonException();
        }
        decide(Status.REJECTED, by, at, note == null || note.isBlank() ? null : note.trim());
    }

    /** The proposer takes it back. */
    public void withdraw(String by, Instant at) {
        pending();
        if (by == null || !by.equals(proposedBy)) {
            throw new IllegalStateException("Only the person who proposed it can withdraw it");
        }
        decide(Status.WITHDRAWN, by, at, null);
    }

    private void pending() {
        if (status != Status.PENDING) {
            throw new IllegalStateException("This was already decided: " + status);
        }
    }

    private void decide(Status s, String by, Instant at, String note) {
        this.status = s;
        this.decidedBy = by;
        this.decidedAt = at;
        this.decisionNote = note;
    }

    public String id() {
        return id;
    }

    public String dfId() {
        return dfId;
    }

    public Kind kind() {
        return kind;
    }

    public String reason() {
        return reason;
    }

    public String proposedBy() {
        return proposedBy;
    }

    public Instant proposedAt() {
        return proposedAt;
    }

    public Status status() {
        return status;
    }

    public String decidedBy() {
        return decidedBy;
    }

    public Instant decidedAt() {
        return decidedAt;
    }

    public String decisionNote() {
        return decisionNote;
    }
}
