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

package com.techeazyhq.cm.bil.app;

import org.springframework.transaction.annotation.Transactional;

import com.techeazyhq.cm.audkit.AuditTrail;
import com.techeazyhq.cm.bil.dom.DfSub;
import com.techeazyhq.cm.bil.dom.DfSubRepoPort;
import com.techeazyhq.cm.bil.dom.PendingPlnChgReqExistsException;
import com.techeazyhq.cm.bil.dom.PlnChgReq;
import com.techeazyhq.cm.bil.dom.PlnChgReqRepoPort;
import com.techeazyhq.cm.bil.dom.SubPln;
import com.techeazyhq.cm.bil.dom.SubPlnRepoPort;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Plan change requests (ADR-039): an Owner or Administrator at a fiduciary asks for another plan; a platform
 * administrator approves it, which changes the plan in the same transaction, or declines it with a reason. Operators
 * see the queue but don't decide, as with changing a plan directly (ADR-017).
 */
public class PlnChgReqHandler {

    private static final int LIST_LIMIT = 200;

    private final PlnChgReqRepoPort reqs;
    private final DfSubRepoPort subs;
    private final SubPlnRepoPort plans;
    private final AuditTrail audit;

    public PlnChgReqHandler(PlnChgReqRepoPort reqs, DfSubRepoPort subs, SubPlnRepoPort plans) {
        this(reqs, subs, plans, AuditTrail.NONE);
    }

    public PlnChgReqHandler(PlnChgReqRepoPort reqs, DfSubRepoPort subs, SubPlnRepoPort plans, AuditTrail audit) {
        this.reqs = reqs;
        this.subs = subs;
        this.plans = plans;
        this.audit = audit;
    }

    @Transactional
    public Mono<PlnChgReq> request(BilCaller caller, String toPlnName, String note) {
        if (!caller.isDf() || !caller.mayRequestPlan()) {
            return Mono.error(new DomRuleViolation(403, "may-not-request-plan",
                    "Only an Owner or an Administrator at the fiduciary can ask for another plan"));
        }
        return Mono.zip(currentPlan(caller.dfId()), knownPlan(toPlnName))
                .flatMap(current -> {
                    PlnChgReq req;
                    try {
                        req = PlnChgReq.request(caller.dfId(), current.getT1().name(), current.getT2().name(), note,
                                caller.subject(), caller.name());
                    } catch (IllegalArgumentException e) {
                        return Mono.error(new DomRuleViolation(400, "invalid-plan-change-request", e.getMessage()));
                    }
                    return reqs.save(req).flatMap(saved -> recorded("plan-change.requested", saved));
                })
                .onErrorMap(PendingPlnChgReqExistsException.class, e -> new DomRuleViolation(409,
                        "plan-change-request-pending", "You already asked for a plan change. Withdraw it to ask for another."));
    }

    @Transactional
    public Mono<PlnChgReq> withdraw(BilCaller caller, String id) {
        if (!caller.isDf() || !caller.mayRequestPlan()) {
            return Mono.error(new DomRuleViolation(403, "may-not-request-plan",
                    "Only an Owner or an Administrator at the fiduciary can withdraw a plan change request"));
        }
        return pending(id)
                .filter(req -> req.dfId().equals(caller.dfId()))
                .switchIfEmpty(Mono.error(notFound(id)))
                .flatMap(req -> decide(req, () -> req.withdraw(caller.subject(), caller.name())))
                .flatMap(req -> recorded("plan-change.withdrawn", req));
    }

    /** Changes the fiduciary's plan to the one asked for, and records who approved it. */
    @Transactional
    public Mono<PlnChgReq> approve(BilCaller caller, String id, String note) {
        return requireAdmin(caller)
                .then(pending(id))
                .flatMap(req -> Mono.zip(subscription(req.dfId()), knownPlan(req.toPlnName()))
                        .flatMap(subAndPlan -> {
                            DfSub sub = subAndPlan.getT1();
                            sub.assignPlan(subAndPlan.getT2().id());
                            return subs.save(sub);
                        })
                        .then(decide(req, () -> req.approve(caller.subject(), caller.name(), note))))
                .flatMap(req -> recorded("plan-change.approved", req));
    }

    @Transactional
    public Mono<PlnChgReq> decline(BilCaller caller, String id, String reason) {
        return requireAdmin(caller)
                .then(pending(id))
                .flatMap(req -> decide(req, () -> req.decline(caller.subject(), caller.name(), reason)))
                .flatMap(req -> recorded("plan-change.declined", req));
    }

    /** The fiduciary's own requests, newest first. */
    public Flux<PlnChgReq> mine(BilCaller caller) {
        if (!caller.isDf()) {
            return Flux.error(new DomRuleViolation(403, "not-a-fiduciary", "Only a fiduciary has plan change requests"));
        }
        return reqs.findByDfId(caller.dfId(), LIST_LIMIT);
    }

    /** Every fiduciary's requests, for platform staff: those waiting, or all. */
    public Flux<PlnChgReq> list(BilCaller caller, PlnChgReq.Sts sts) {
        if (!caller.staff()) {
            return Flux.error(new DomRuleViolation(403, "not-platform-staff", "Only platform staff see every request"));
        }
        return reqs.findBySts(sts, LIST_LIMIT);
    }

    private Mono<PlnChgReq> decide(PlnChgReq req, Runnable decision) {
        try {
            decision.run();
        } catch (IllegalArgumentException e) {
            return Mono.error(new DomRuleViolation(400, "invalid-decision", e.getMessage()));
        } catch (IllegalStateException e) {
            return Mono.error(new DomRuleViolation(409, "plan-change-request-decided", e.getMessage()));
        }
        return reqs.save(req);
    }

    private Mono<PlnChgReq> recorded(String action, PlnChgReq req) {
        return audit.record(action, req.dfId(), req.id(), AuditTrail.details("from", req.fromPlnName(),
                "to", req.toPlnName(), "note", req.decisionNote() != null ? req.decisionNote() : req.note()))
                .thenReturn(req);
    }

    private Mono<Void> requireAdmin(BilCaller caller) {
        return caller.admin() ? Mono.empty() : Mono.error(new DomRuleViolation(403, "not-platform-admin",
                "Only a platform administrator approves or declines plan changes"));
    }

    private Mono<PlnChgReq> pending(String id) {
        return reqs.findById(id)
                .switchIfEmpty(Mono.error(notFound(id)))
                .flatMap(req -> req.sts() == PlnChgReq.Sts.PENDING ? Mono.just(req)
                        : Mono.error(new DomRuleViolation(409, "plan-change-request-decided",
                                "This request was already " + req.sts().name().toLowerCase())));
    }

    private Mono<DfSub> subscription(String dfId) {
        return subs.findByDfId(dfId).switchIfEmpty(Mono.error(new DomRuleViolation(404, "df-sub-not-found",
                "This fiduciary has no subscription yet; it starts when the fiduciary is activated")));
    }

    private Mono<SubPln> currentPlan(String dfId) {
        return subscription(dfId).flatMap(sub -> plans.findById(sub.subPlnId()));
    }

    private Mono<SubPln> knownPlan(String name) {
        return plans.findByName(name == null ? "" : name.trim())
                .switchIfEmpty(Mono.error(new DomRuleViolation(400, "unknown-sub-pln", "There is no plan named " + name)));
    }

    private static DomRuleViolation notFound(String id) {
        return new DomRuleViolation(404, "plan-change-request-not-found", "No plan change request " + id);
    }
}
