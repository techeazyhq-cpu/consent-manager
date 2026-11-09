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
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.transaction.annotation.Transactional;

import com.techeazyhq.cm.audkit.AuditTrail;
import com.techeazyhq.cm.dfreg.dom.BillingClosurePort;
import com.techeazyhq.cm.dfreg.dom.Df;
import com.techeazyhq.cm.dfreg.dom.DfMember;
import com.techeazyhq.cm.dfreg.dom.DfMemberDirectoryPort;
import com.techeazyhq.cm.dfreg.dom.DfRepoPort;
import com.techeazyhq.cm.dfreg.dom.DfRole;
import com.techeazyhq.cm.dfreg.dom.EmailPort;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * A fiduciary's Owner asking to leave the platform, and calling it off once (ADR-064). Leaving begins a wind-down
 * (30 days by default); nothing new is accepted during it, exactly as when suspended, and closing follows when it ends.
 * The Owner confirms by typing the organisation's name. The whole team is emailed, and each step is in the audit ledger.
 * Staff ending a contract needs a second staff member's approval and is not here yet.
 *
 * <p>Billing follows the wind-down (ADR-109): it ends when the departure begins (the fiduciary can use nothing, so it is not billed for it),
 * and begins again if the departure is called off.
 */
public class DfLeavingHandler {

    private static final System.Logger LOG = System.getLogger(DfLeavingHandler.class.getName());
    /**
     * How long Billing is waited for (ADR-109). The request is the gateway's, which gives up at 10 seconds, and a Billing that does not
     * answer must not take the whole request with it: the departure is the person's to make, so ending billing is waited for briefly,
     * and calling a departure off, which must not leave the fiduciary unbilled, a little longer, then fails cleanly.
     */
    static final Duration END_BILLING_WAIT = Duration.ofSeconds(3);
    static final Duration RESUME_BILLING_WAIT = Duration.ofSeconds(5);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH).withZone(ZoneOffset.UTC);

    private final DfRepoPort dfs;
    private final DfMemberDirectoryPort members;
    private final BillingClosurePort billing;
    private final EmailPort email;
    private final AuditTrail audit;
    private final Clock clock;
    private final int windDownDays;

    public DfLeavingHandler(DfRepoPort dfs, DfMemberDirectoryPort members, BillingClosurePort billing, EmailPort email, AuditTrail audit,
                            Clock clock, int windDownDays) {
        this.dfs = dfs;
        this.members = members;
        this.billing = billing;
        this.email = email;
        this.audit = audit;
        this.clock = clock;
        this.windDownDays = windDownDays;
    }

    /** Where the caller's fiduciary stands. Any member can see it. */
    public Mono<Df> status(DfTeamCaller caller) {
        return member(caller).then(Mono.defer(() -> own(caller)));
    }

    @Transactional
    public Mono<Df> leave(DfTeamCaller caller, String confirmName) {
        return owner(caller).then(Mono.defer(() -> own(caller))).flatMap(df -> {
            if (confirmName == null || !confirmName.trim().equalsIgnoreCase(df.name())) {
                return Mono.<Df>error(new DomRuleViolation(400, "invalid-request",
                        "Type the organisation's name exactly to confirm that it is leaving"));
            }
            try {
                df.startLeaving(caller.id(), clock.instant(), windDownDays);
            } catch (IllegalStateException e) {
                return Mono.<Df>error(new DomRuleViolation(409, "df-cannot-leave", e.getMessage()));
            }
            return dfs.save(df).flatMap(saved -> audit.record("tenant.leaving", saved.id(), saved.id(),
                            AuditTrail.details("closesOn", DAY.format(saved.leavesAt()), "windDownDays", String.valueOf(windDownDays)))
                    .then(stopBilling(saved))
                    .then(tell(saved, "leaving " + saved.leavesAt().toEpochMilli(),
                            saved.name() + " is leaving Consent Manager",
                            saved.name() + " has asked to leave Consent Manager. A wind-down of " + windDownDays
                                    + " days has begun and ends on " + DAY.format(saved.leavesAt()) + ". Until then no new consent "
                                    + "requests are accepted and API keys stop working; your records stay readable.\n\n"
                                    + "An Owner can call this off once before " + DAY.format(saved.leavesAt()) + ", in the DF Console."))
                    .thenReturn(saved));
        });
    }

    @Transactional
    public Mono<Df> cancel(DfTeamCaller caller) {
        return owner(caller).then(Mono.defer(() -> own(caller))).flatMap(df -> {
            try {
                df.cancelLeaving();
            } catch (Df.CancelUsedException e) {
                return Mono.<Df>error(new DomRuleViolation(409, "leave-cancel-used", e.getMessage()));
            } catch (IllegalStateException e) {
                return Mono.<Df>error(new DomRuleViolation(409, "df-not-leaving", e.getMessage()));
            }
            return dfs.save(df).flatMap(saved -> audit.record("tenant.leaving-cancelled", saved.id(), saved.id())
                    .then(resumeBilling(saved))
                    .then(tell(saved, "leaving-cancelled " + clock.instant().toEpochMilli(),
                            saved.name() + " is staying with Consent Manager",
                            "The departure of " + saved.name() + " was called off. The fiduciary is back as it was before. "
                                    + "A departure can be cancelled once, so asking to leave again cannot be undone."))
                    .thenReturn(saved));
        });
    }

    /** Starts a departure that two members of staff agreed (ADR-064 D7); the Owner and team are told. */
    @Transactional
    public Mono<Df> startByStaff(String dfId, String proposedBy, String approvedBy, String reason) {
        return dfs.findById(dfId).switchIfEmpty(Mono.error(new DomRuleViolation(404, "df-not-found", "No DF " + dfId))).flatMap(df -> {
            try {
                df.startLeaving(proposedBy, clock.instant(), windDownDays);
            } catch (IllegalStateException e) {
                return Mono.<Df>error(new DomRuleViolation(409, "df-cannot-leave", e.getMessage()));
            }
            return dfs.save(df).flatMap(saved -> audit.record("tenant.leaving", saved.id(), saved.id(),
                            AuditTrail.details("closesOn", DAY.format(saved.leavesAt()), "windDownDays", String.valueOf(windDownDays),
                                    "initiatedBy", "platform staff", "proposedBy", proposedBy, "approvedBy", approvedBy, "reason", reason))
                    .then(stopBilling(saved))
                    .then(tell(saved, "leaving " + saved.leavesAt().toEpochMilli(),
                            "Consent Manager is ending its agreement with " + saved.name(),
                            "Consent Manager has ended its agreement with " + saved.name() + ". A wind-down of " + windDownDays
                                    + " days has begun and ends on " + DAY.format(saved.leavesAt()) + ". Until then no new consent "
                                    + "requests are accepted and API keys stop working; your records stay readable.\n\nReason given: "
                                    + reason + "\n\nIf you think this is a mistake, contact the platform before " + DAY.format(saved.leavesAt()) + "."))
                    .thenReturn(saved));
        });
    }

    /** Calls a departure off after two members of staff agreed; it does not use the Owner's one cancellation. */
    @Transactional
    public Mono<Df> cancelByStaff(String dfId, String proposedBy, String approvedBy, String reason) {
        return dfs.findById(dfId).switchIfEmpty(Mono.error(new DomRuleViolation(404, "df-not-found", "No DF " + dfId))).flatMap(df -> {
            try {
                df.cancelLeavingByStaff();
            } catch (IllegalStateException e) {
                return Mono.<Df>error(new DomRuleViolation(409, "df-not-leaving", e.getMessage()));
            }
            return dfs.save(df).flatMap(saved -> audit.record("tenant.leaving-cancelled", saved.id(), saved.id(),
                            AuditTrail.details("initiatedBy", "platform staff", "proposedBy", proposedBy, "approvedBy", approvedBy, "reason", reason))
                    .then(resumeBilling(saved))
                    .then(tell(saved, "leaving-cancelled " + clock.instant().toEpochMilli(),
                            saved.name() + " is staying with Consent Manager",
                            "The ending of the agreement with " + saved.name() + " was called off. The fiduciary is back as it was before."))
                    .thenReturn(saved));
        });
    }

    /**
     * Ends the fiduciary's billing when its departure begins (ADR-109). Best effort: if Billing cannot be reached the departure still
     * begins, and the closing that ends the wind-down ends billing too (ADR-084), so the worst case is the billing it had before.
     * Ending twice changes nothing.
     */
    private Mono<Void> stopBilling(Df df) {
        return billing.endBilling(df.id()).timeout(END_BILLING_WAIT).onErrorResume(e -> {
            LOG.log(System.Logger.Level.WARNING, "[CM-LEAVING-BILLING-FAILED] Billing could not be told that DF {0} is leaving; it is told "
                    + "again when it closes: {1}", df.id(), e.getMessage());
            return Mono.empty();
        });
    }

    /**
     * Bills the fiduciary again when its departure is called off (ADR-109). Not best effort: a fiduciary that comes back unbilled would
     * go unnoticed, so if Billing cannot be reached or is slow the cancellation fails, with a problem the person can retry, and (being one
     * transaction) is not kept.
     */
    private Mono<Void> resumeBilling(Df df) {
        return billing.resumeBilling(df.id()).timeout(RESUME_BILLING_WAIT)
                .onErrorMap(e -> !(e instanceof DomRuleViolation), e -> {
                    LOG.log(System.Logger.Level.WARNING, "Billing could not resume for DF {0}, so its departure was not called off: {1}",
                            df.id(), e.getMessage());
                    return new DomRuleViolation(503, "billing-unavailable",
                            "Billing could not be reached, so the departure was not called off. Nothing has changed; try again shortly");
                });
    }

    /** Tells every member of the team. Best effort: the change is made whether or not the mail goes. */
    private Mono<Void> tell(Df df, String ref, String subject, String body) {
        return members.list(df.id())
                .concatMap(m -> email.send(m.email(), subject, "Hello,\n\n" + body + "\n\nThis is an automated message from Consent Manager.",
                        ref + " " + df.id() + " " + m.id()).onErrorResume(e -> {
                    LOG.log(System.Logger.Level.WARNING, "Could not tell {0} about the departure of DF {1}: {2}", m.email(), df.id(), e.getMessage());
                    return Mono.empty();
                }))
                .onErrorResume(e -> Flux.empty())
                .then();
    }

    private Mono<Df> own(DfTeamCaller caller) {
        return dfs.findById(caller.dfId()).switchIfEmpty(Mono.error(new DomRuleViolation(404, "df-not-found", "No DF " + caller.dfId())));
    }

    private static Mono<Void> member(DfTeamCaller caller) {
        if (caller.platformStaff() || caller.role() == null || caller.dfId() == null) {
            return Mono.error(new DomRuleViolation(403, "not-permitted", "Only people at the fiduciary"));
        }
        return Mono.empty();
    }

    private static Mono<Void> owner(DfTeamCaller caller) {
        return member(caller).then(Mono.defer(() -> caller.role() == DfRole.OWNER ? Mono.<Void>empty()
                : Mono.<Void>error(new DomRuleViolation(403, "not-permitted", "Only an Owner can end the organisation's use of the platform"))));
    }
}
