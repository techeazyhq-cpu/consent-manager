package com.techeazyhq.cm.dsr.app;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.techeazyhq.cm.audkit.AuditTrail;
import com.techeazyhq.cm.dsr.dom.DpErasure;
import com.techeazyhq.cm.dsr.dom.DpErasurePorts;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Orchestrates a principal closing their account (ADR-021 §3, ADR-059). Each step is sent to the service that owns it,
 * or run here, and the saga moves on when it answers. {@link #redrive} runs every minute: a step without an answer
 * after {@link #PATIENCE} is sent again, and after {@link DpErasure#MAX_ATTEMPTS} the saga is STUCK, logged as an error
 * for alerting, and shown to staff, who can retry it once the cause is fixed.
 */
public class DpErasureHandler {

    private static final Logger LOG = LoggerFactory.getLogger(DpErasureHandler.class);
    static final Duration PATIENCE = Duration.ofMinutes(2);
    /** What the principal types to confirm, so an account is never closed by a stray click. */
    public static final String CONFIRMATION = "CLOSE MY ACCOUNT";

    private final DpErasurePorts.Repo erasures;
    private final DpErasurePorts.Steps steps;
    private final DpErasurePorts.Identity identity;
    private final DpReqHandler requests;
    private final AuditTrail audit;
    private final Clock clock;

    public DpErasureHandler(DpErasurePorts.Repo erasures, DpErasurePorts.Steps steps, DpErasurePorts.Identity identity,
                            DpReqHandler requests, AuditTrail audit, Clock clock) {
        this.erasures = erasures;
        this.steps = steps;
        this.identity = identity;
        this.requests = requests;
        this.audit = audit;
        this.clock = clock;
    }

    /** The principal closes their account. Their email, from their token, is used once, for the notice. */
    public Mono<DpErasure> request(DsrCaller caller, String email, String confirmation) {
        if (!caller.isDp()) {
            return Mono.error(new DomRuleViolation(403, "not-a-data-principal", "Only a data principal closes their account"));
        }
        if (confirmation == null || !CONFIRMATION.equalsIgnoreCase(confirmation.trim())) {
            return Mono.error(new DomRuleViolation(400, "confirmation-required",
                    "Type " + CONFIRMATION + " to confirm that you want your account closed"));
        }
        DpErasure erasure = DpErasure.start(caller.dpId(), email, clock.instant());
        return erasures.save(erasure)
                .onErrorMap(DpErasurePorts.ErasureRunningException.class,
                        e -> new DomRuleViolation(409, "erasure-running", e.getMessage()))
                .flatMap(saved -> audit.record("dp-erasure.requested", null, saved.id()).thenReturn(saved))
                .flatMap(this::advance);
    }

    /** The principal's latest erasure: how far it got, while they can still sign in to see it. */
    public Mono<DpErasure> mine(DsrCaller caller) {
        if (!caller.isDp()) {
            return Mono.error(new DomRuleViolation(403, "not-a-data-principal", "Only a data principal has an account here"));
        }
        return erasures.findLatestByDpId(caller.dpId());
    }

    /** A service answered that it finished {@code step}. Duplicates and late answers change nothing. */
    public Mono<Void> onStepDone(String erasureId, String step, List<String> cnsReqIds, Integer withdrawn) {
        DpErasure.Step done;
        try {
            done = DpErasure.Step.valueOf(step);
        } catch (IllegalArgumentException | NullPointerException e) {
            LOG.warn("Erasure {}: an answer for an unknown step {}", erasureId, step);
            return Mono.empty();
        }
        return erasures.findById(erasureId)
                .filter(erasure -> erasure.stepDone(done, cnsReqIds, withdrawn == null ? 0 : withdrawn, clock.instant()))
                .flatMap(erasures::save)
                .flatMap(this::afterStep)
                .then();
    }

    /** Sends again whatever has waited too long for an answer. */
    public Mono<Void> redrive() {
        return erasures.findRunning(100)
                .filter(erasure -> erasure.due(clock.instant(), PATIENCE))
                .concatMap(this::advance)
                .onErrorContinue((e, o) -> LOG.warn("Erasure redrive failed for {}", o, e))
                .then();
    }

    // ---- staff ----

    public Flux<DpErasure> list(DsrCaller caller, DpErasure.Sts sts) {
        if (!caller.staff()) {
            return Flux.error(new DomRuleViolation(403, "not-platform-staff", "Only platform staff see erasures"));
        }
        return erasures.findBySts(sts, 200);
    }

    /** Starts a stuck erasure's step again, once its cause is fixed. */
    public Mono<DpErasure> retry(DsrCaller caller, String id) {
        if (!caller.staff()) {
            return Mono.error(new DomRuleViolation(403, "not-platform-staff", "Only platform staff retry erasures"));
        }
        return erasures.findById(id)
                .switchIfEmpty(Mono.error(new DomRuleViolation(404, "erasure-not-found", "No erasure " + id)))
                .flatMap(erasure -> {
                    try {
                        erasure.retry();
                    } catch (IllegalStateException e) {
                        return Mono.error(new DomRuleViolation(409, "erasure-not-stuck", e.getMessage()));
                    }
                    return erasures.save(erasure);
                })
                .flatMap(erasure -> audit.record("dp-erasure.retried", null, erasure.id(),
                        AuditTrail.details("step", erasure.step().name())).thenReturn(erasure))
                .flatMap(this::advance);
    }

    // ---- the saga ----

    /** Runs or sends the current step, unless it has been tried too often. */
    private Mono<DpErasure> advance(DpErasure erasure) {
        if (erasure.sts() != DpErasure.Sts.RUNNING) {
            return Mono.just(erasure);
        }
        if (erasure.giveUpIfExhausted(erasure.lastError())) {
            LOG.error("Erasure {} is STUCK at {} after {} attempts: {}. See docs/runbooks/erasure.md", erasure.id(),
                    erasure.step(), erasure.attempts(), erasure.lastError());
            return erasures.save(erasure)
                    .flatMap(saved -> audit.record("dp-erasure.stuck", null, saved.id(),
                            AuditTrail.details("step", saved.step().name(), "error", saved.lastError())).thenReturn(saved));
        }
        DpErasure.Step step = erasure.step();
        erasure.attempted(clock.instant());
        if (step.remote()) {
            return erasures.save(erasure)
                    .flatMap(saved -> steps.send(saved).thenReturn(saved))
                    .onErrorResume(e -> failed(erasure, e));
        }
        return erasures.save(erasure)
                .flatMap(saved -> runLocal(saved)
                        .flatMap(n -> erasures.findById(saved.id()))
                        .filter(fresh -> fresh.stepDone(step, null, 0, clock.instant()))
                        .flatMap(erasures::save)
                        .flatMap(this::afterStep)
                        .defaultIfEmpty(saved))
                .onErrorResume(e -> failed(erasure, e));
    }

    private Mono<Long> runLocal(DpErasure erasure) {
        return switch (erasure.step()) {
            case REQUESTS -> requests.closeAccount(erasure.dpId());
            case IDENTITY -> identity.delete(erasure.dpId()).thenReturn(1L);
            default -> Mono.error(new IllegalStateException(erasure.step() + " is not run here"));
        };
    }

    /** After a step: the next one, or the record that the account is closed. */
    private Mono<DpErasure> afterStep(DpErasure erasure) {
        if (erasure.sts() == DpErasure.Sts.DONE) {
            LOG.info("Erasure {} done", erasure.id());
            return audit.record("dp-erasure.completed", null, erasure.id(),
                    AuditTrail.details("consentsWithdrawn", String.valueOf(erasure.consentsWithdrawn())))
                    .thenReturn(erasure);
        }
        return advance(erasure);
    }

    /** The attempt failed; it is tried again by {@link #redrive} until it gives up. */
    private Mono<DpErasure> failed(DpErasure erasure, Throwable e) {
        LOG.warn("Erasure {} step {} attempt {} failed", erasure.id(), erasure.step(), erasure.attempts(), e);
        return erasures.findById(erasure.id())
                .flatMap(fresh -> {
                    fresh.failed(e.getClass().getSimpleName() + ": " + e.getMessage());
                    return erasures.save(fresh);
                })
                .onErrorResume(ignored -> Mono.just(erasure));
    }
}
