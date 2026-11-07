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

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.techeazyhq.cm.dfreg.dom.Df;
import com.techeazyhq.cm.dfreg.dom.DfRepoPort;
import com.techeazyhq.cm.dfreg.dom.EmailPort;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Tells a fiduciary, and the platform's staff, that its usage reached a share of its monthly allowance (ADR-060).
 * cm-bil-svc raises the alert and says so in an event; cm-df-reg-svc holds the fiduciary's contact address, so it sends
 * the emails, through cm-ntf-svc, and the address never travels in an event or over HTTP. Idempotent: each message has a
 * reference built from the alert, so an event delivered twice is one email to the mail service.
 */
public class OnUsgThresholdEvtHandler {

    private static final Logger LOG = LoggerFactory.getLogger(OnUsgThresholdEvtHandler.class);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)
            .withZone(ZoneOffset.UTC);

    /** What cm-bil-svc's {@code UsgThresholdEvt} says. */
    public record Threshold(String dfId, String period, int threshold, long used, long quota, String planName,
                            Instant resetsAt) {

        boolean exhausted() {
            return threshold >= 100;
        }

        String ref() {
            return "cm-usage-" + dfId + "-" + period + "-" + threshold;
        }
    }

    private final DfRepoPort dfs;
    private final EmailPort email;
    private final List<String> platformEmails;

    public OnUsgThresholdEvtHandler(DfRepoPort dfs, EmailPort email, List<String> platformEmails) {
        this.dfs = dfs;
        this.email = email;
        this.platformEmails = platformEmails == null ? List.of() : List.copyOf(platformEmails);
    }

    public Mono<Void> handle(Threshold t) {
        return dfs.findById(t.dfId())
                .switchIfEmpty(Mono.fromRunnable(() -> LOG.warn("Usage alert for unknown fiduciary {}", t.dfId())))
                .flatMapMany(df -> Flux.concat(toFiduciary(df, t), toPlatform(df, t)))
                .then();
    }

    private Mono<Void> toFiduciary(Df df, Threshold t) {
        if (df.contactEmail() == null || df.contactEmail().isBlank()) {
            return Mono.empty();
        }
        return email.send(df.contactEmail(), fiduciarySubject(t), fiduciaryBody(df.name(), t), t.ref() + "-df");
    }

    private Flux<Void> toPlatform(Df df, Threshold t) {
        return Flux.fromIterable(platformEmails).index()
                .concatMap(entry -> email.send(entry.getT2(), platformSubject(df.name(), t), platformBody(df.name(), t),
                        t.ref() + "-platform-" + entry.getT1()));
    }

    static String fiduciarySubject(Threshold t) {
        return t.exhausted() ? "Your consent request allowance for " + t.period() + " is used up"
                : "You've used " + t.threshold() + "% of this month's consent request allowance";
    }

    static String fiduciaryBody(String name, Threshold t) {
        String used = name + " has used " + t.used() + " of its " + t.quota() + " consent requests for " + t.period()
                + " on the " + t.planName() + " plan. The allowance starts again on " + DAY.format(t.resetsAt()) + ".";
        String next = t.exhausted()
                ? "New consent requests are refused until then."
                : "Once it is used up, new consent requests are refused until then.";
        return "Hello,\n\n" + used + "\n\n" + next + " To avoid that, an Owner or an Administrator can ask for a larger "
                + "plan in the DF Console, under Billing.\n\nThis is an automated message from Consent Manager.";
    }

    static String platformSubject(String name, Threshold t) {
        return "Usage alert: " + name + " at " + t.threshold() + "% of its " + t.period() + " allowance";
    }

    static String platformBody(String name, Threshold t) {
        return name + " (" + t.dfId() + ") has used " + t.used() + " of " + t.quota() + " consent requests for "
                + t.period() + " on the " + t.planName() + " plan, reaching " + t.threshold() + "%. The fiduciary has "
                + "been emailed. See the billing overview in CM Admin.";
    }
}
