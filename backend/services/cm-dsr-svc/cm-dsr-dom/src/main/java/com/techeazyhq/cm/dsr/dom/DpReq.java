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

package com.techeazyhq.cm.dsr.dom;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * A request a data principal makes through the platform (ADR-021, ADR-058). Two kinds, in this first slice:
 * <ul>
 * <li>{@link Knd#RELAY}: about a fiduciary's data, the right named by {@link DpRgt}. The platform passes it to the
 * fiduciary and records what the fiduciary answered; it never fulfils it itself (ADR-023). It always concerns one of
 * the principal's own consents ({@code cnsReqId}), which is how the fiduciary knows who is asking without the platform
 * sending it any contact detail.</li>
 * <li>{@link Knd#GRIEVANCE}: a complaint about the platform itself, answered by platform staff.</li>
 * </ul>
 * The deadline is fixed when the request is received and never moves. A request is acknowledged, then resolved or
 * refused; a refusal cites a legal ground. The principal can withdraw it while it is open.
 */
public final class DpReq {

    public enum Knd {
        RELAY, GRIEVANCE
    }

    public enum Sts {
        OPEN, ACKNOWLEDGED, RESOLVED, REFUSED, WITHDRAWN;

        public boolean open() {
            return this == OPEN || this == ACKNOWLEDGED;
        }
    }

    public static final int MAX_DETAILS = 2000;
    public static final int MAX_RESPONSE = 2000;
    public static final int MAX_GROUND = 300;

    private final String id;
    private final Knd knd;
    private final String dpId;
    private final String dfId;
    private final String cnsReqId;
    private final String prpsCode;
    private final DpRgt rgt;
    private final String details;
    private final Instant receivedAt;
    private final Instant dueBy;
    private Sts sts;
    private Instant acknowledgedAt;
    private Instant closedAt;
    private String closedBy;
    private String closedByName;
    private String response;
    private String refusalGround;

    private DpReq(String id, Knd knd, String dpId, String dfId, String cnsReqId, String prpsCode, DpRgt rgt,
                  String details, Instant receivedAt, Instant dueBy, Sts sts, Instant acknowledgedAt, Instant closedAt,
                  String closedBy, String closedByName, String response, String refusalGround) {
        this.id = id;
        this.knd = knd;
        this.dpId = dpId;
        this.dfId = dfId;
        this.cnsReqId = cnsReqId;
        this.prpsCode = prpsCode;
        this.rgt = rgt;
        this.details = details;
        this.receivedAt = receivedAt;
        this.dueBy = dueBy;
        this.sts = sts;
        this.acknowledgedAt = acknowledgedAt;
        this.closedAt = closedAt;
        this.closedBy = closedBy;
        this.closedByName = closedByName;
        this.response = response;
        this.refusalGround = refusalGround;
    }

    /**
     * A request about a fiduciary's data, for the consent {@code cnsReqId} the principal holds with it.
     *
     * @throws IllegalArgumentException without a right, a consent or a fiduciary, or with overlong details
     */
    public static DpReq relay(String dpId, String dfId, String cnsReqId, String prpsCode, DpRgt rgt, String details,
                              Duration answerWithin, Instant now) {
        if (rgt == null) {
            throw new IllegalArgumentException("Say what you are asking the organisation to do");
        }
        if (cnsReqId == null || cnsReqId.isBlank() || dfId == null || dfId.isBlank()) {
            throw new IllegalArgumentException("Choose the consent your request is about");
        }
        String text = clean(details, MAX_DETAILS, "details");
        if (rgt.needsDetails() && text == null) {
            throw new IllegalArgumentException("Say what should change");
        }
        return new DpReq(UUID.randomUUID().toString(), Knd.RELAY, required(dpId), dfId, cnsReqId, prpsCode, rgt, text,
                now, now.plus(answerWithin), Sts.OPEN, null, null, null, null, null, null);
    }

    /**
     * A complaint about the platform.
     *
     * @throws IllegalArgumentException without details, or with overlong details
     */
    public static DpReq grievance(String dpId, String details, Duration answerWithin, Instant now) {
        String text = clean(details, MAX_DETAILS, "details");
        if (text == null) {
            throw new IllegalArgumentException("Say what went wrong");
        }
        return new DpReq(UUID.randomUUID().toString(), Knd.GRIEVANCE, required(dpId), null, null, null, null, text, now,
                now.plus(answerWithin), Sts.OPEN, null, null, null, null, null, null);
    }

    public static DpReq rehydrate(String id, Knd knd, String dpId, String dfId, String cnsReqId, String prpsCode,
                                  DpRgt rgt, String details, Instant receivedAt, Instant dueBy, Sts sts,
                                  Instant acknowledgedAt, Instant closedAt, String closedBy, String closedByName,
                                  String response, String refusalGround) {
        return new DpReq(id, knd, dpId, dfId, cnsReqId, prpsCode, rgt, details, receivedAt, dueBy, sts, acknowledgedAt,
                closedAt, closedBy, closedByName, response, refusalGround);
    }

    /** The answerer has seen it and is working on it. Acknowledging twice is harmless. */
    public void acknowledge(Instant now) {
        requireOpen();
        if (sts == Sts.OPEN) {
            sts = Sts.ACKNOWLEDGED;
            acknowledgedAt = now;
        }
    }

    /** Done: {@code response} says what was done, for the principal to read. */
    public void resolve(String by, String byName, String response, Instant now) {
        String text = clean(response, MAX_RESPONSE, "response");
        if (text == null) {
            throw new IllegalArgumentException("Say what was done");
        }
        close(Sts.RESOLVED, by, byName, text, null, now);
    }

    /** Not done, and why: a refusal names the legal ground it rests on (ADR-021). */
    public void refuse(String by, String byName, String response, String ground, Instant now) {
        String why = clean(ground, MAX_GROUND, "legal ground");
        if (why == null) {
            throw new IllegalArgumentException("Name the legal ground for refusing");
        }
        String text = clean(response, MAX_RESPONSE, "response");
        if (text == null) {
            throw new IllegalArgumentException("Explain the refusal to the person");
        }
        close(Sts.REFUSED, by, byName, text, why, now);
    }

    /** The principal no longer wants it. */
    public void withdraw(String by, Instant now) {
        close(Sts.WITHDRAWN, by, null, null, null, now);
    }

    /** Still open past its deadline. */
    public boolean overdue(Instant now) {
        return sts.open() && now.isAfter(dueBy);
    }

    private void close(Sts outcome, String by, String byName, String text, String ground, Instant now) {
        requireOpen();
        if (acknowledgedAt == null && outcome != Sts.WITHDRAWN) {
            acknowledgedAt = now;
        }
        sts = outcome;
        closedAt = now;
        closedBy = by;
        closedByName = byName;
        response = text;
        refusalGround = ground;
    }

    private void requireOpen() {
        if (!sts.open()) {
            throw new IllegalStateException("This request was already " + sts.name().toLowerCase());
        }
    }

    private static String required(String dpId) {
        if (dpId == null || dpId.isBlank()) {
            throw new IllegalArgumentException("Only a data principal makes a request");
        }
        return dpId;
    }

    private static String clean(String text, int max, String what) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.length() > max) {
            throw new IllegalArgumentException("Keep the " + what + " to " + max + " characters");
        }
        return trimmed;
    }

    public String id() {
        return id;
    }

    public Knd knd() {
        return knd;
    }

    public String dpId() {
        return dpId;
    }

    public String dfId() {
        return dfId;
    }

    public String cnsReqId() {
        return cnsReqId;
    }

    public String prpsCode() {
        return prpsCode;
    }

    public DpRgt rgt() {
        return rgt;
    }

    public String details() {
        return details;
    }

    public Instant receivedAt() {
        return receivedAt;
    }

    public Instant dueBy() {
        return dueBy;
    }

    public Sts sts() {
        return sts;
    }

    public Instant acknowledgedAt() {
        return acknowledgedAt;
    }

    public Instant closedAt() {
        return closedAt;
    }

    public String closedBy() {
        return closedBy;
    }

    public String closedByName() {
        return closedByName;
    }

    public String response() {
        return response;
    }

    public String refusalGround() {
        return refusalGround;
    }
}
