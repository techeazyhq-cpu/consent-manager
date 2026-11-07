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

package com.techeazyhq.cm.dsr.app;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import org.springframework.transaction.annotation.Transactional;

import com.techeazyhq.cm.audkit.AuditTrail;
import com.techeazyhq.cm.dsr.dom.DpCnsPort;
import com.techeazyhq.cm.dsr.dom.DpReq;
import com.techeazyhq.cm.dsr.dom.DpReqEvtPort;
import com.techeazyhq.cm.dsr.dom.DpReqRepoPort;
import com.techeazyhq.cm.dsr.dom.DpRgt;
import com.techeazyhq.cm.dsr.dom.OpenDpReqExistsException;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Principal requests (ADR-021, ADR-058). A principal raises a request about one of their consents, which is relayed to
 * that consent's fiduciary, or a grievance about the platform. The fiduciary answers its own requests; platform staff
 * answer grievances. Each side sees only its own: another's request is "not found", as an unknown id is.
 */
public class DpReqHandler {

    /** Open requests a principal may have at once, so the platform can't be used to flood a fiduciary. */
    static final int MAX_OPEN = 20;
    private static final int LIST_LIMIT = 200;

    private final DpReqRepoPort reqs;
    private final DpCnsPort consents;
    private final DpReqEvtPort events;
    private final AuditTrail audit;
    private final DsrDeadlines deadlines;
    private final Clock clock;

    public DpReqHandler(DpReqRepoPort reqs, DpCnsPort consents, DpReqEvtPort events, AuditTrail audit,
                        DsrDeadlines deadlines, Clock clock) {
        this.reqs = reqs;
        this.consents = consents;
        this.events = events;
        this.audit = audit;
        this.deadlines = deadlines;
        this.clock = clock;
    }

    // ---- the principal ----

    /** A request about one of the principal's consents, relayed to its fiduciary. */
    @Transactional
    public Mono<DpReq> relay(DsrCaller caller, String cnsReqId, String right, String details) {
        DpRgt rgt;
        try {
            rgt = right == null ? null : DpRgt.valueOf(right.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Mono.error(new DomRuleViolation(400, "invalid-right",
                    "right is ACCESS, CORRECTION, ERASURE, GRIEVANCE or NOMINATION"));
        }
        if (cnsReqId == null || cnsReqId.isBlank()) {
            return Mono.error(new DomRuleViolation(400, "invalid-dp-request", "Choose the consent your request is about"));
        }
        return requireDp(caller)
                .then(withinLimit(caller))
                .then(consents.find(cnsReqId.trim(), caller.token()))
                .switchIfEmpty(Mono.error(new DomRuleViolation(404, "cns-not-found",
                        "You have no consent " + cnsReqId.trim())))
                .flatMap(cns -> create(() -> DpReq.relay(caller.dpId(), cns.dfId(), cns.cnsReqId(), cns.prpsCode(), rgt,
                        details, deadlines.relay(), now())))
                .flatMap(req -> reqs.save(req)
                        .flatMap(saved -> events.publish(message(saved, DpReqEvtPort.RECEIVED, m -> {
                            m.put("right", saved.rgt().name());
                            m.put("dueBy", saved.dueBy().toString());
                        })).thenReturn(saved)))
                .flatMap(req -> recorded("dp-request.raised", req))
                .onErrorMap(OpenDpReqExistsException.class, e -> new DomRuleViolation(409, "dp-request-open",
                        "You already asked this organisation for this. You'll see its answer here."));
    }

    /** A complaint about the platform, for its staff. */
    @Transactional
    public Mono<DpReq> grievance(DsrCaller caller, String details) {
        return requireDp(caller)
                .then(withinLimit(caller))
                .then(create(() -> DpReq.grievance(caller.dpId(), details, deadlines.grievance(), now())))
                .flatMap(reqs::save)
                .flatMap(req -> recorded("dp-grievance.raised", req));
    }

    @Transactional
    public Mono<DpReq> withdraw(DsrCaller caller, String id) {
        return requireDp(caller)
                .then(reqs.findById(id))
                .filter(req -> req.dpId().equals(caller.dpId()))
                .switchIfEmpty(Mono.error(notFound(id)))
                .flatMap(req -> change(req, () -> req.withdraw(caller.subject(), now())))
                .flatMap(req -> req.knd() == DpReq.Knd.RELAY
                        ? events.publish(message(req, DpReqEvtPort.WITHDRAWN, m -> { })).thenReturn(req)
                        : Mono.just(req))
                .flatMap(req -> recorded(req.knd() == DpReq.Knd.RELAY ? "dp-request.withdrawn" : "dp-grievance.withdrawn",
                        req));
    }

    /** The principal's own requests and grievances, newest first. */
    public Flux<DpReq> mine(DsrCaller caller) {
        return requireDp(caller).thenMany(reqs.findByDpId(caller.dpId(), LIST_LIMIT));
    }

    /**
     * The principal is closing their account (ADR-059): their open requests are withdrawn, and each fiduciary told as
     * when they withdraw one themself; then the words of every request and answer are erased. Safe to run again.
     */
    @Transactional
    public Mono<Long> closeAccount(String dpId) {
        return reqs.findOpenByDpId(dpId)
                .concatMap(req -> {
                    req.withdraw(dpId, now());
                    return reqs.save(req)
                            .flatMap(saved -> saved.knd() == DpReq.Knd.RELAY
                                    ? events.publish(message(saved, DpReqEvtPort.WITHDRAWN, m -> { })).thenReturn(saved)
                                    : Mono.just(saved))
                            .flatMap(saved -> recorded(saved.knd() == DpReq.Knd.RELAY ? "dp-request.withdrawn"
                                    : "dp-grievance.withdrawn", saved));
                })
                .then(reqs.eraseTextOf(dpId));
    }

    // ---- the fiduciary ----

    /** Requests relayed to the caller's fiduciary: every member and system reads them. */
    public Flux<DpReq> forFiduciary(DsrCaller caller, DpReq.Sts sts) {
        if (!caller.isDf()) {
            return Flux.error(new DomRuleViolation(403, "not-a-fiduciary", "Only a fiduciary has requests relayed to it"));
        }
        return reqs.findByDfId(caller.dfId(), sts, LIST_LIMIT);
    }

    public Mono<DpReq> oneForFiduciary(DsrCaller caller, String id) {
        if (!caller.isDf()) {
            return Mono.error(new DomRuleViolation(403, "not-a-fiduciary", "Only a fiduciary has requests relayed to it"));
        }
        return relayed(caller, id);
    }

    @Transactional
    public Mono<DpReq> acknowledge(DsrCaller caller, DpReq.Knd knd, String id) {
        return answerable(caller, knd, id)
                .flatMap(req -> change(req, () -> req.acknowledge(now())))
                .flatMap(req -> recorded(action(req, "acknowledged"), req));
    }

    @Transactional
    public Mono<DpReq> resolve(DsrCaller caller, DpReq.Knd knd, String id, String response) {
        return answerable(caller, knd, id)
                .flatMap(req -> change(req, () -> req.resolve(caller.subject(), caller.name(), response, now())))
                .flatMap(req -> recorded(action(req, "resolved"), req));
    }

    @Transactional
    public Mono<DpReq> refuse(DsrCaller caller, DpReq.Knd knd, String id, String response, String ground) {
        return answerable(caller, knd, id)
                .flatMap(req -> change(req, () -> req.refuse(caller.subject(), caller.name(), response, ground, now())))
                .flatMap(req -> recorded(action(req, "refused"), req));
    }

    // ---- platform staff ----

    /** Grievances about the platform, for its staff. */
    public Flux<DpReq> grievances(DsrCaller caller, DpReq.Sts sts) {
        if (!caller.staff()) {
            return Flux.error(new DomRuleViolation(403, "not-platform-staff", "Only platform staff answer grievances"));
        }
        return reqs.findGrievances(sts, LIST_LIMIT);
    }

    // ---- shared ----

    /**
     * The request, if this caller answers it: its own fiduciary's relayed request, for an Owner, Administrator,
     * Consent manager or system; a grievance, for platform staff.
     */
    private Mono<DpReq> answerable(DsrCaller caller, DpReq.Knd knd, String id) {
        if (knd == DpReq.Knd.GRIEVANCE) {
            if (!caller.staff()) {
                return Mono.error(new DomRuleViolation(403, "not-platform-staff", "Only platform staff answer grievances"));
            }
            return reqs.findById(id)
                    .filter(req -> req.knd() == DpReq.Knd.GRIEVANCE)
                    .switchIfEmpty(Mono.error(notFound(id)));
        }
        if (!caller.isDf()) {
            return Mono.error(new DomRuleViolation(403, "may-not-answer", "Only the organisation asked answers a request"));
        }
        if (!caller.mayAnswer()) {
            return Mono.error(new DomRuleViolation(403, "may-not-answer",
                    "Only an Owner, Administrator or Consent manager answers requests"));
        }
        return relayed(caller, id);
    }

    private Mono<DpReq> relayed(DsrCaller caller, String id) {
        return reqs.findById(id)
                .filter(req -> req.knd() == DpReq.Knd.RELAY && caller.dfId().equals(req.dfId()))
                .switchIfEmpty(Mono.error(notFound(id)));
    }

    private Mono<Void> requireDp(DsrCaller caller) {
        return caller.isDp() ? Mono.empty()
                : Mono.error(new DomRuleViolation(403, "not-a-data-principal", "Only a data principal makes requests"));
    }

    private Mono<Void> withinLimit(DsrCaller caller) {
        return reqs.countOpenByDpId(caller.dpId())
                .flatMap(open -> open >= MAX_OPEN
                        ? Mono.error(new DomRuleViolation(429, "too-many-open-requests", "You have " + open
                                + " requests open. Wait for answers, or withdraw one, before making another."))
                        : Mono.empty());
    }

    private static Mono<DpReq> create(java.util.function.Supplier<DpReq> factory) {
        try {
            return Mono.just(factory.get());
        } catch (IllegalArgumentException e) {
            return Mono.error(new DomRuleViolation(400, "invalid-dp-request", e.getMessage()));
        }
    }

    private Mono<DpReq> change(DpReq req, Runnable change) {
        try {
            change.run();
        } catch (IllegalArgumentException e) {
            return Mono.error(new DomRuleViolation(400, "invalid-answer", e.getMessage()));
        } catch (IllegalStateException e) {
            return Mono.error(new DomRuleViolation(409, "dp-request-closed", e.getMessage()));
        }
        return reqs.save(req);
    }

    private static String action(DpReq req, String what) {
        return (req.knd() == DpReq.Knd.RELAY ? "dp-request." : "dp-grievance.") + what;
    }

    /** Recorded against the fiduciary for a relayed request, so its own audit shows it; never the principal's words. */
    private Mono<DpReq> recorded(String action, DpReq req) {
        return audit.record(action, req.dfId(), req.id(), AuditTrail.details(
                        "right", req.rgt() == null ? null : req.rgt().name(),
                        "cnsReqId", req.cnsReqId(),
                        "sts", req.sts().name(),
                        "ground", req.refusalGround()))
                .thenReturn(req);
    }

    private static DpReqEvtPort.DfMessage message(DpReq req, String type, Consumer<Map<String, Object>> more) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("requestId", req.id());
        data.put("cnsReqId", req.cnsReqId());
        if (req.prpsCode() != null) {
            data.put("prpsCode", req.prpsCode());
        }
        more.accept(data);
        return new DpReqEvtPort.DfMessage(UUID.randomUUID().toString(), type, req.dfId(), Instant.now(), data);
    }

    private Instant now() {
        return clock.instant();
    }

    private static DomRuleViolation notFound(String id) {
        return new DomRuleViolation(404, "dp-request-not-found", "No request " + id);
    }
}
