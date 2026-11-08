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
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import org.springframework.transaction.annotation.Transactional;

import com.techeazyhq.cm.audkit.AuditTrail;
import com.techeazyhq.cm.dfreg.dom.Df;
import com.techeazyhq.cm.dfreg.dom.DfFactory;
import com.techeazyhq.cm.dfreg.dom.DfPrfl;
import com.techeazyhq.cm.dfreg.dom.DfRepoPort;
import com.techeazyhq.cm.dfreg.dom.DfSignup;
import com.techeazyhq.cm.dfreg.dom.DfSignupRepoPort;
import com.techeazyhq.cm.dfreg.dom.EmailPort;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Mono;

/**
 * An organisation applying for itself (ADR-065). Anyone can apply, so the page is held to account: the address is proven
 * with a token mailed to it before anything is created, a person is held to three applications a day and the whole
 * platform to a daily cap, and the same answer is given whether or not the address is already a fiduciary. Confirming the
 * address creates the fiduciary as PENDING, exactly as staff registering one does, and staff activate or reject it. Nobody
 * is a fiduciary's Owner until staff activate it.
 */
public class DfSignupHandler {

    private static final System.Logger LOG = System.getLogger(DfSignupHandler.class.getName());

    /** What the page sends. */
    public record Application(String name, String contactEmail, String legalName, String industry, String website) {
    }

    private final DfSignupRepoPort signups;
    private final DfRepoPort dfs;
    private final EmailPort email;
    private final AuditTrail audit;
    private final Clock clock;
    private final String consoleUrl;
    private final int perAddressPerDay;
    private final int perDay;
    private final Duration ttl;
    private final List<String> staffEmails;

    public DfSignupHandler(DfSignupRepoPort signups, DfRepoPort dfs, EmailPort email, AuditTrail audit, Clock clock,
                           String consoleUrl, int perAddressPerDay, int perDay, Duration ttl, List<String> staffEmails) {
        this.signups = signups;
        this.dfs = dfs;
        this.email = email;
        this.audit = audit;
        this.clock = clock;
        this.consoleUrl = consoleUrl.endsWith("/") ? consoleUrl.substring(0, consoleUrl.length() - 1) : consoleUrl;
        this.perAddressPerDay = perAddressPerDay;
        this.perDay = perDay;
        this.ttl = ttl;
        this.staffEmails = staffEmails == null ? List.of() : List.copyOf(staffEmails);
    }

    /** Takes an application and mails the address a link to confirm it. Says nothing about whether the address is known. */
    @Transactional
    public Mono<Void> apply(Application a) {
        Instant now = clock.instant();
        String address;
        try {
            // the same checks as registering: a name and a well-formed address, and a profile that is valid as entered
            Df probe = DfFactory.register(a.name(), a.contactEmail(), new DfPrfl(a.legalName(), null, a.website(), null, a.industry(),
                    null, null, null, null), null);
            address = probe.contactEmail().toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            return Mono.error(new DomRuleViolation(400, "invalid-df-profile", e.getMessage()));
        }
        Instant since = now.minus(Duration.ofDays(1));
        return signups.countByEmailSince(address, since).flatMap(forAddress -> {
            if (forAddress >= perAddressPerDay) {
                return Mono.<Void>error(new DomRuleViolation(429, "signup-rate-limited",
                        "Too many applications were made for this address today. Try again tomorrow."));
            }
            return signups.countSince(since).flatMap(today -> {
                if (today >= perDay) {
                    return Mono.<Void>error(new DomRuleViolation(429, "signup-rate-limited",
                            "Sign-up is busy. Try again later."));
                }
                DfSignup.Issued issued = DfSignup.create(a.name().trim(), address, a.legalName(), a.industry(), a.website(), now, ttl);
                String link = consoleUrl + "/#/signup/verify?id=" + issued.signup().id() + "&token=" + issued.token();
                return signups.save(issued.signup())
                        .then(Mono.defer(() -> email.send(address, "Confirm your Consent Manager application",
                                "Hello,\n\nSomeone applied for " + a.name().trim() + " to use Consent Manager with this address. To go on, "
                                        + "confirm that it is yours:\n\n" + link + "\n\nThe link works once and for " + ttl.toHours()
                                        + " hours. If it was not you, ignore this message and nothing will happen.\n\nThis is an automated "
                                        + "message from Consent Manager.", "signup " + issued.signup().id())));
            });
        });
    }

    /** Confirms the address: creates the fiduciary as PENDING and tells the applicant and staff. */
    @Transactional
    public Mono<Void> verify(String id, String token) {
        Instant now = clock.instant();
        Mono<Void> invalid = Mono.error(new DomRuleViolation(400, "signup-invalid-token",
                "This confirmation link is not valid, has expired or was already used. Apply again."));
        if (id == null || id.isBlank() || token == null || token.isBlank()) {
            return invalid;
        }
        return signups.findById(id).filter(s -> s.matches(token) && s.usable(now)).switchIfEmpty(invalid.cast(DfSignup.class)).flatMap(s ->
                dfs.findAll().filter(df -> s.contactEmail().equalsIgnoreCase(df.contactEmail())).hasElements().flatMap(known -> {
                    if (known) {
                        return Mono.<Void>error(new DomRuleViolation(409, "df-already-registered",
                                "An organisation is already registered with this address. Sign in to the DF Console, or contact the platform."));
                    }
                    Df df = DfFactory.register(s.name(), s.contactEmail(), new DfPrfl(s.legalName(), null, s.website(), null, s.industry(),
                            null, null, null, null), null);
                    df.markSelfService(now);
                    s.verified(df.id(), now);
                    return dfs.save(df).then(Mono.defer(() -> signups.save(s)))
                            .then(Mono.defer(() -> audit.record("tenant.signed-up", df.id(), df.id(), AuditTrail.details("name", df.name()))))
                            .then(Mono.defer(() -> tell(s.contactEmail(), "We received your application to Consent Manager",
                                    "Hello,\n\nThank you for confirming your address. Your application for " + df.name() + " is with our team. "
                                            + "We will write to this address when it has been looked at: you can sign in once it is approved.\n\n"
                                            + "This is an automated message from Consent Manager.", "signup-received " + df.id())))
                            .then(Mono.defer(() -> tellStaff(df)));
                }));
    }

    /** Staff declining an application that is still waiting; the applicant is told the reason. */
    @Transactional
    public Mono<Df> reject(String dfId, String reason) {
        return dfs.findById(dfId).switchIfEmpty(Mono.error(new DomRuleViolation(404, "df-not-found", "No DF " + dfId))).flatMap(df -> {
            try {
                df.reject(reason, clock.instant());
            } catch (IllegalArgumentException e) {
                return Mono.<Df>error(new DomRuleViolation(400, "invalid-request", e.getMessage()));
            } catch (IllegalStateException e) {
                return Mono.<Df>error(new DomRuleViolation(409, "df-cannot-reject", e.getMessage()));
            }
            return dfs.save(df).flatMap(saved -> audit.record("tenant.rejected", dfId, dfId, AuditTrail.details("reason", saved.rejectionReason()))
                    .then(tell(saved.contactEmail(), "About your application to Consent Manager",
                            "Hello,\n\nWe are not able to approve the application for " + saved.name() + ". The reason:\n\n"
                                    + saved.rejectionReason() + "\n\nIf you think this is a mistake, reply to the platform's support.\n\n"
                                    + "This is an automated message from Consent Manager.", "signup-rejected " + dfId))
                    .thenReturn(saved));
        });
    }

    /** Deletes applications nobody confirmed, a week after their link expired: they hold an address no one proved. */
    public Mono<Long> purgeStale() {
        return signups.deleteUnconfirmedExpiredBefore(clock.instant().minus(Duration.ofDays(7)));
    }

    private Mono<Void> tellStaff(Df df) {
        return reactor.core.publisher.Flux.fromIterable(staffEmails).index().concatMap(e -> tell(e.getT2(), "New application: " + df.name(),
                df.name() + " (" + df.contactEmail() + ") confirmed its address and waits for activation in CM Admin, under Awaiting activation.",
                "signup-staff " + df.id() + " " + e.getT1())).then();
    }

    /** Best effort: the change is made whether or not the mail goes. */
    private Mono<Void> tell(String to, String subject, String body, String ref) {
        return email.send(to, subject, body, ref).onErrorResume(e -> {
            LOG.log(System.Logger.Level.WARNING, "Could not send \"{0}\" to {1}: {2}", subject, to, e.getMessage());
            return Mono.empty();
        });
    }
}
