package com.techeazyhq.cm.dsr.dom;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A principal closing their account: the erasure of what the platform holds about them (ADR-021 §3, ADR-059), as an
 * orchestrated saga. The steps run in a fixed order, each owned by one service; identity, the point after which the
 * principal can no longer sign in, comes last. Each step is idempotent and sent again until it answers; a step that
 * doesn't answer after {@link #MAX_ATTEMPTS} sends stops the saga as {@link Sts#STUCK} for staff to look at and retry.
 *
 * <p>Consent records stay, under the principal's pseudonymous id only, for the legal hold (DPDPA First Schedule B4).
 * The email address is kept only while the saga runs, for the notice, and is cleared when it ends.
 */
public final class DpErasure {

    public enum Sts {
        RUNNING, DONE, STUCK
    }

    /** In order. Remote steps are done by another service; local ones here. */
    public enum Step {
        NOTICE_AND_DEVICES(true), CONSENTS(true), AUDIT(true), REQUESTS(false), IDENTITY(false);

        private final boolean remote;

        Step(boolean remote) {
            this.remote = remote;
        }

        public boolean remote() {
            return remote;
        }

        Step next() {
            return ordinal() + 1 < values().length ? values()[ordinal() + 1] : null;
        }
    }

    public static final int MAX_ATTEMPTS = 8;

    private final String id;
    private final String dpId;
    private String email;
    private final Instant requestedAt;
    private Sts sts;
    private Step step;
    private int attempts;
    private Instant dispatchedAt;
    private Instant finishedAt;
    private String lastError;
    private List<String> cnsReqIds;
    private int consentsWithdrawn;

    private DpErasure(String id, String dpId, String email, Instant requestedAt, Sts sts, Step step, int attempts,
                      Instant dispatchedAt, Instant finishedAt, String lastError, List<String> cnsReqIds,
                      int consentsWithdrawn) {
        this.id = id;
        this.dpId = dpId;
        this.email = email;
        this.requestedAt = requestedAt;
        this.sts = sts;
        this.step = step;
        this.attempts = attempts;
        this.dispatchedAt = dispatchedAt;
        this.finishedAt = finishedAt;
        this.lastError = lastError;
        this.cnsReqIds = cnsReqIds == null ? List.of() : List.copyOf(cnsReqIds);
        this.consentsWithdrawn = consentsWithdrawn;
    }

    public static DpErasure start(String dpId, String email, Instant now) {
        if (dpId == null || dpId.isBlank()) {
            throw new IllegalArgumentException("Only a data principal closes their account");
        }
        return new DpErasure(UUID.randomUUID().toString(), dpId, email, now, Sts.RUNNING, Step.values()[0], 0, null,
                null, null, List.of(), 0);
    }

    public static DpErasure rehydrate(String id, String dpId, String email, Instant requestedAt, Sts sts, Step step,
                                      int attempts, Instant dispatchedAt, Instant finishedAt, String lastError,
                                      List<String> cnsReqIds, int consentsWithdrawn) {
        return new DpErasure(id, dpId, email, requestedAt, sts, step, attempts, dispatchedAt, finishedAt, lastError,
                cnsReqIds, consentsWithdrawn);
    }

    /** The current step was sent (or run) once more. */
    public void attempted(Instant now) {
        requireRunning();
        attempts++;
        dispatchedAt = now;
    }

    /**
     * {@code done} finished. An answer for any other step than the current one (a duplicate, or one that arrives
     * after a resend already moved on) changes nothing: false.
     */
    public boolean stepDone(Step done, List<String> foundCnsReqIds, int withdrawn, Instant now) {
        if (sts != Sts.RUNNING || done != step) {
            return false;
        }
        if (done == Step.CONSENTS) {
            cnsReqIds = foundCnsReqIds == null ? List.of() : List.copyOf(foundCnsReqIds);
            consentsWithdrawn = withdrawn;
        }
        step = done.next();
        attempts = 0;
        dispatchedAt = null;
        lastError = null;
        if (step == null) {
            sts = Sts.DONE;
            finishedAt = now;
            email = null;
        }
        return true;
    }

    /** Whether the current step has waited {@code patience} for an answer, and should be sent again. */
    public boolean due(Instant now, Duration patience) {
        return sts == Sts.RUNNING && (dispatchedAt == null || !now.isBefore(dispatchedAt.plus(patience)));
    }

    /** Gives up on the current step after {@link #MAX_ATTEMPTS}: true when it just became stuck. */
    public boolean giveUpIfExhausted(String error) {
        if (sts == Sts.RUNNING && attempts >= MAX_ATTEMPTS) {
            sts = Sts.STUCK;
            lastError = error == null ? "No answer after " + attempts + " attempts" : error;
            return true;
        }
        return false;
    }

    public void failed(String error) {
        lastError = error == null ? null : error.length() > 300 ? error.substring(0, 300) : error;
    }

    /** Staff start a stuck step again, from its first attempt. */
    public void retry() {
        if (sts != Sts.STUCK) {
            throw new IllegalStateException("Only a stuck erasure is retried");
        }
        sts = Sts.RUNNING;
        attempts = 0;
        dispatchedAt = null;
    }

    private void requireRunning() {
        if (sts != Sts.RUNNING) {
            throw new IllegalStateException("This erasure is " + sts.name().toLowerCase());
        }
    }

    public String id() {
        return id;
    }

    public String dpId() {
        return dpId;
    }

    public String email() {
        return email;
    }

    public Instant requestedAt() {
        return requestedAt;
    }

    public Sts sts() {
        return sts;
    }

    /** The step running or stuck; null once done. */
    public Step step() {
        return step;
    }

    public int attempts() {
        return attempts;
    }

    public Instant dispatchedAt() {
        return dispatchedAt;
    }

    public Instant finishedAt() {
        return finishedAt;
    }

    public String lastError() {
        return lastError;
    }

    public List<String> cnsReqIds() {
        return cnsReqIds;
    }

    public int consentsWithdrawn() {
        return consentsWithdrawn;
    }
}
