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
    private final String contactEmail;
    private final DfPrfl prfl;
    private final String subPlnName;
    private final Instant registeredAt;
    private DfSts sts;
    private String cmDfApiClientSecret;
    private Instant activatedAt;
    private Instant suspendedAt;
    private String suspensionReason;

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
