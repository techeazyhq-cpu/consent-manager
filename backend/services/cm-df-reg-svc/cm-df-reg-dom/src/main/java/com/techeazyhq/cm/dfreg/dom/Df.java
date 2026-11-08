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

import com.techeazyhq.cm.domkit.Agg;

/** The Data Fiduciary Aggregate Root (plan §3). */
public final class Df extends Agg<String> {

    private final String name;
    private String contactEmail;
    private DfPrfl prfl;
    private final String subPlnName;
    private final Instant registeredAt;
    private DfSts sts;
    private String cmDfApiClientSecret;
    private Instant activatedAt;
    private Instant suspendedAt;
    private String suspensionReason;
    private DfSts leavingFrom;
    private Instant leavingAt;
    private Instant leavesAt;
    private String leavingBy;
    private int leaveCancellations;
    private Instant closedAt;
    private String legalHoldReason;
    private Instant legalHoldAt;
    private Instant purgedAt;

    Df(String id, String name, String contactEmail, DfPrfl prfl, String subPlnName, DfSts sts, Instant registeredAt,
       String cmDfApiClientSecret, Instant activatedAt) {
        super(id);
        this.name = name;
        this.contactEmail = contactEmail;
        this.prfl = prfl == null ? DfPrfl.EMPTY : prfl;
        this.subPlnName = subPlnName;
        this.sts = sts;
        this.registeredAt = registeredAt;
        this.cmDfApiClientSecret = cmDfApiClientSecret;
        this.activatedAt = activatedAt;
    }

    public String name() {
        return name;
    }

    public String contactEmail() {
        return contactEmail;
    }

    public DfPrfl prfl() {
        return prfl;
    }

    /** The plan chosen at onboarding, which cm-bil-svc subscribes the fiduciary to on activation; null means FREE. */
    public String subPlnName() {
        return subPlnName;
    }

    public DfSts sts() {
        return sts;
    }

    public Instant registeredAt() {
        return registeredAt;
    }

    public String cmDfApiClientSecret() {
        return cmDfApiClientSecret;
    }

    public Instant activatedAt() {
        return activatedAt;
    }

    /** Why a legal hold stops the retention purge for this fiduciary (ADR-064), or null when there is none. */
    public String legalHoldReason() {
        return legalHoldReason;
    }

    public Instant legalHoldAt() {
        return legalHoldAt;
    }

    /** When the retention purge finished with this fiduciary, or null. */
    public Instant purgedAt() {
        return purgedAt;
    }

    /** A legal hold, whatever the status: nothing of this fiduciary is purged while it is held. */
    public void placeLegalHold(String reason, Instant at) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A reason for the legal hold is needed");
        }
        if (reason.trim().length() > 500) {
            throw new IllegalArgumentException("The reason must be at most 500 characters");
        }
        this.legalHoldReason = reason.trim();
        this.legalHoldAt = at;
    }

    public void releaseLegalHold() {
        if (legalHoldReason == null) {
            throw new IllegalStateException("There is no legal hold to release");
        }
        this.legalHoldReason = null;
        this.legalHoldAt = null;
    }

    /** Whether the retention purge may run for this fiduciary now: closed, not yet purged, not held, and old enough. */
    public boolean purgeable(Instant cutoff) {
        return sts == DfSts.CLOSED && purgedAt == null && legalHoldReason == null && closedAt != null && closedAt.isBefore(cutoff);
    }

    /** Notes that the purge has deleted everything it holds for this fiduciary. */
    public void markPurged(Instant at) {
        if (sts != DfSts.CLOSED) {
            throw new IllegalStateException("Only a closed DF is purged, not one in status " + sts);
        }
        this.purgedAt = at;
    }

    void restoreRetention(String reason, Instant holdAt, Instant purged) {
        this.legalHoldReason = reason;
        this.legalHoldAt = holdAt;
        this.purgedAt = purged;
    }

    /** When the fiduciary was closed, or null. */
    public Instant closedAt() {
        return closedAt;
    }

    /**
     * Ends the wind-down (ADR-064): CLOSED is final. Its API client secret is dropped, so no token can be issued, and its
     * profile is erased except what is kept as the record of who it was: the display and legal names and the registration
     * numbers.
     */
    public void close(Instant at) {
        if (sts != DfSts.LEAVING) {
            throw new IllegalStateException("Only a DF that is leaving can be closed, not one in status " + sts);
        }
        this.sts = DfSts.CLOSED;
        this.closedAt = at;
        this.cmDfApiClientSecret = null;
        this.contactEmail = "[erased]";
        this.prfl = new DfPrfl(prfl.legalName(), null, null, null, null, prfl.cin(), prfl.gstin(), null, null);
    }

    void restoreClosed(Instant at) {
        this.closedAt = at;
    }

    /** When the wind-down began, or null when the fiduciary is not leaving. */
    public Instant leavingAt() {
        return leavingAt;
    }

    /** When the wind-down ends and the fiduciary is closed, or null when it is not leaving. */
    public Instant leavesAt() {
        return leavesAt;
    }

    /** Who asked to leave, or null. */
    public String leavingBy() {
        return leavingBy;
    }

    /** A departure can be cancelled once (ADR-064 D5). */
    public boolean canCancelLeaving() {
        return sts == DfSts.LEAVING && leaveCancellations == 0;
    }

    /** Starts the wind-down of an active or suspended fiduciary; it ends {@code windDownDays} days later. */
    public void startLeaving(String by, Instant at, int windDownDays) {
        if (sts != DfSts.ACTIVE && sts != DfSts.SUSPENDED) {
            throw new IllegalStateException("Only an active or suspended DF can leave, not one in status " + sts);
        }
        if (windDownDays < 1) {
            throw new IllegalArgumentException("The wind-down is at least a day");
        }
        this.leavingFrom = sts;
        this.sts = DfSts.LEAVING;
        this.leavingAt = at;
        this.leavesAt = at.plus(java.time.Duration.ofDays(windDownDays));
        this.leavingBy = by;
    }

    /** Calls the departure off, once: back to the state it left from. */
    public void cancelLeaving() {
        if (sts != DfSts.LEAVING) {
            throw new IllegalStateException("Only a DF that is leaving can cancel, not one in status " + sts);
        }
        if (leaveCancellations > 0) {
            throw new CancelUsedException();
        }
        this.sts = leavingFrom == null ? DfSts.ACTIVE : leavingFrom;
        this.leavingFrom = null;
        this.leavingAt = null;
        this.leavesAt = null;
        this.leavingBy = null;
        this.leaveCancellations++;
    }

    /** The one cancellation has been used. */
    public static final class CancelUsedException extends IllegalStateException {
        CancelUsedException() {
            super("A departure can be cancelled once, and this one already was");
        }
    }

    /** The state it leaves from, to return to on cancelling. */
    public DfSts leavingFrom() {
        return leavingFrom;
    }

    public int leaveCancellations() {
        return leaveCancellations;
    }

    void restoreLeaving(DfSts from, Instant at, Instant ends, String by, int cancellations) {
        this.leavingFrom = from;
        this.leavingAt = at;
        this.leavesAt = ends;
        this.leavingBy = by;
        this.leaveCancellations = cancellations;
    }

    /** When the fiduciary was suspended, or null when it is not. */
    public Instant suspendedAt() {
        return suspendedAt;
    }

    /** Why platform staff suspended it, or null when it is not suspended. */
    public String suspensionReason() {
        return suspensionReason;
    }

    /** Stops an active fiduciary (ADR-064). A reason is needed, since it is shown to the team and kept in the audit trail. */
    public void suspend(String reason, Instant at) {
        if (sts != DfSts.ACTIVE) {
            throw new IllegalStateException("Only an active DF can be suspended, not one in status " + sts);
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A reason for the suspension is needed");
        }
        if (reason.trim().length() > 500) {
            throw new IllegalArgumentException("The reason must be at most 500 characters");
        }
        this.sts = DfSts.SUSPENDED;
        this.suspendedAt = at;
        this.suspensionReason = reason.trim();
    }

    /** Returns a suspended fiduciary to ACTIVE. Its keys, team and consents were never touched. */
    public void resume() {
        if (sts != DfSts.SUSPENDED) {
            throw new IllegalStateException("Only a suspended DF can be resumed, not one in status " + sts);
        }
        this.sts = DfSts.ACTIVE;
        this.suspendedAt = null;
        this.suspensionReason = null;
    }

    void restoreSuspension(Instant at, String reason) {
        this.suspendedAt = at;
        this.suspensionReason = reason;
    }

    /** A new API client secret replaces the old one, which stops working at once. Only an active fiduciary has one. */
    public void replaceClientSecret(String clientSecret) {
        if (sts != DfSts.ACTIVE) {
            throw new IllegalStateException("Cannot replace the client secret of a DF in status " + sts);
        }
        this.cmDfApiClientSecret = clientSecret;
    }

    /** {@code clientSecret} comes from Keycloak Admin API provisioning, performed by -infra before this is called (plan §10). */
    public void activate(String clientSecret) {
        if (sts != DfSts.PENDING) {
            throw new IllegalStateException("Cannot activate a DF in status " + sts);
        }
        this.sts = DfSts.ACTIVE;
        this.cmDfApiClientSecret = clientSecret;
        this.activatedAt = Instant.now();
        raise(DfActivatedEvt.of(id(), name, subPlnName));
    }
}
