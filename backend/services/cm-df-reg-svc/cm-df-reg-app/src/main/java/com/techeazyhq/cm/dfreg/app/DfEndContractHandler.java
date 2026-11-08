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


package com.techeazyhq.cm.dfreg.app;

import java.time.Clock;

import org.springframework.transaction.annotation.Transactional;

import com.techeazyhq.cm.audkit.AuditTrail;
import com.techeazyhq.cm.dfreg.dom.Df;
import com.techeazyhq.cm.dfreg.dom.DfRepoPort;
import com.techeazyhq.cm.dfreg.dom.DfSts;
import com.techeazyhq.cm.dfreg.dom.LeaveProposal;
import com.techeazyhq.cm.dfreg.dom.LeaveProposalRepoPort;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Platform staff ending a fiduciary's contract, or calling that off, with a second person's approval (ADR-064 D7). One
 * member of staff proposes, with a reason; a different member approves or rejects it, and only then does the departure
 * start or stop. At most one proposal waits per fiduciary. Each step is in the audit ledger with who and why.
 */
public class DfEndContractHandler {

    private final LeaveProposalRepoPort proposals;
    private final DfRepoPort dfs;
    private final DfLeavingHandler leaving;
    private final AuditTrail audit;
    private final Clock clock;

    public DfEndContractHandler(LeaveProposalRepoPort proposals, DfRepoPort dfs, DfLeavingHandler leaving, AuditTrail audit, Clock clock) {
        this.proposals = proposals;
        this.dfs = dfs;
        this.leaving = leaving;
        this.audit = audit;
        this.clock = clock;
    }

    public Flux<LeaveProposal> list(LeaveProposal.Status status, String dfId) {
        return proposals.find(status, dfId);
    }

    @Transactional
    public Mono<LeaveProposal> propose(String staffId, String dfId, LeaveProposal.Kind kind, String reason) {
        return dfs.findById(dfId).switchIfEmpty(Mono.error(new DomRuleViolation(404, "df-not-found", "No DF " + dfId)))
                .flatMap(df -> eligible(df, kind).then(proposals.findPendingByDfId(dfId).hasElement()).flatMap(pending -> {
                    if (pending) {
                        return Mono.<LeaveProposal>error(new DomRuleViolation(409, "leave-proposal-pending",
                                "A proposal for this fiduciary already waits for a second person"));
                    }
                    LeaveProposal proposal;
                    try {
                        proposal = LeaveProposal.propose(dfId, kind, reason, staffId, clock.instant());
                    } catch (IllegalArgumentException e) {
                        return Mono.<LeaveProposal>error(new DomRuleViolation(400, "invalid-request", e.getMessage()));
                    }
                    return proposals.save(proposal).flatMap(saved -> audit.record("tenant.end-contract-proposed", dfId, saved.id(),
                            AuditTrail.details("kind", kind.name(), "reason", saved.reason())).thenReturn(saved));
                }));
    }

    @Transactional
    public Mono<LeaveProposal> approve(String staffId, String proposalId) {
        return find(proposalId).flatMap(p -> {
            try {
                p.approve(staffId, clock.instant());
            } catch (LeaveProposal.SamePersonException e) {
                return Mono.<LeaveProposal>error(new DomRuleViolation(403, "leave-proposal-same-person", e.getMessage()));
            } catch (IllegalStateException e) {
                return Mono.<LeaveProposal>error(new DomRuleViolation(409, "leave-proposal-decided", e.getMessage()));
            }
            Mono<Df> apply = p.kind() == LeaveProposal.Kind.START
                    ? leaving.startByStaff(p.dfId(), p.proposedBy(), staffId, p.reason())
                    : leaving.cancelByStaff(p.dfId(), p.proposedBy(), staffId, p.reason());
            return apply.then(proposals.save(p)).flatMap(saved -> audit.record("tenant.end-contract-approved", p.dfId(), saved.id(),
                    AuditTrail.details("kind", p.kind().name(), "proposedBy", p.proposedBy())).thenReturn(saved));
        });
    }

    @Transactional
    public Mono<LeaveProposal> reject(String staffId, String proposalId, String note) {
        return find(proposalId).flatMap(p -> {
            try {
                p.reject(staffId, clock.instant(), note);
            } catch (LeaveProposal.SamePersonException e) {
                return Mono.<LeaveProposal>error(new DomRuleViolation(403, "leave-proposal-same-person", e.getMessage()));
            } catch (IllegalStateException e) {
                return Mono.<LeaveProposal>error(new DomRuleViolation(409, "leave-proposal-decided", e.getMessage()));
            }
            return proposals.save(p).flatMap(saved -> audit.record("tenant.end-contract-rejected", p.dfId(), saved.id(),
                    AuditTrail.details("kind", p.kind().name(), "proposedBy", p.proposedBy())).thenReturn(saved));
        });
    }

    @Transactional
    public Mono<LeaveProposal> withdraw(String staffId, String proposalId) {
        return find(proposalId).flatMap(p -> {
            try {
                p.withdraw(staffId, clock.instant());
            } catch (IllegalStateException e) {
                boolean decided = p.status() != LeaveProposal.Status.PENDING;
                return Mono.<LeaveProposal>error(decided ? new DomRuleViolation(409, "leave-proposal-decided", e.getMessage())
                        : new DomRuleViolation(403, "not-permitted", e.getMessage()));
            }
            return proposals.save(p).flatMap(saved -> audit.record("tenant.end-contract-withdrawn", p.dfId(), saved.id()).thenReturn(saved));
        });
    }

    private Mono<Void> eligible(Df df, LeaveProposal.Kind kind) {
        boolean ok = kind == LeaveProposal.Kind.START ? df.sts() == DfSts.ACTIVE || df.sts() == DfSts.SUSPENDED : df.sts() == DfSts.LEAVING;
        return ok ? Mono.empty() : Mono.error(new DomRuleViolation(409, kind == LeaveProposal.Kind.START ? "df-cannot-leave" : "df-not-leaving",
                "A fiduciary in status " + df.sts() + " cannot have its contract " + (kind == LeaveProposal.Kind.START ? "ended" : "cancelled")));
    }

    private Mono<LeaveProposal> find(String id) {
        return proposals.findById(id).switchIfEmpty(Mono.error(new DomRuleViolation(404, "leave-proposal-not-found", "No proposal " + id)));
    }
}
