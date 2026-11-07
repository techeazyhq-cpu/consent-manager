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
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.transaction.annotation.Transactional;

import com.techeazyhq.cm.audkit.AuditTrail;
import com.techeazyhq.cm.dfreg.dom.DfRole;
import com.techeazyhq.cm.dfreg.dom.DfTermsAcceptance;
import com.techeazyhq.cm.dfreg.dom.DfTermsRepoPort;
import com.techeazyhq.cm.dfreg.dom.TermsDoc;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Mono;

/**
 * A fiduciary accepting the consent manager's own terms and data processing agreement (ADR-063). Only an Owner can accept,
 * since it binds the organisation; the version accepted must be the current one, so nobody agrees to words they were not
 * shown. Every acceptance is kept, with who and when, and is in the audit ledger.
 */
public class DfTermsHandler {

    /** One document's current version and whether this fiduciary has accepted it. */
    public record Status(TermsDoc doc, String currentVersion, boolean accepted, DfTermsAcceptance acceptance) {
    }

    private final DfTermsRepoPort repo;
    private final Map<TermsDoc, String> current;
    private final AuditTrail audit;
    private final Clock clock;

    public DfTermsHandler(DfTermsRepoPort repo, Map<TermsDoc, String> currentVersions, AuditTrail audit, Clock clock) {
        this.repo = repo;
        this.current = Map.copyOf(currentVersions);
        this.audit = audit;
        this.clock = clock;
    }

    /** Where each document stands for the caller's fiduciary. Any member can see it. */
    public Mono<List<Status>> status(DfTeamCaller caller) {
        return member(caller).then(Mono.defer(() -> statusOf(caller.dfId())));
    }

    /** Platform staff reading a fiduciary's acceptances as evidence, newest first. */
    public Mono<List<DfTermsAcceptance>> evidence(DfTeamCaller caller, String dfId) {
        if (!caller.platformStaff()) {
            return Mono.error(new DomRuleViolation(403, "not-permitted", "Only platform staff read another organisation's acceptances"));
        }
        return repo.findByDfId(dfId).collectList();
    }

    @Transactional
    public Mono<DfTermsAcceptance> accept(DfTeamCaller caller, String docName, String version) {
        return member(caller).then(Mono.defer(() -> {
            if (caller.role() != DfRole.OWNER) {
                return Mono.error(new DomRuleViolation(403, "not-permitted", "Only an Owner can accept these on the organisation's behalf"));
            }
            TermsDoc doc = TermsDoc.parse(docName).orElse(null);
            if (doc == null) {
                return Mono.error(new DomRuleViolation(400, "terms-document-unknown",
                        "Unknown document " + docName + "; the documents are " + Arrays.toString(TermsDoc.values())));
            }
            if (!current.get(doc).equals(version)) {
                return Mono.error(new DomRuleViolation(409, "terms-version-outdated",
                        "The current version of " + doc + " is " + current.get(doc) + ", not " + version));
            }
            DfTermsAcceptance fresh = DfTermsAcceptance.of(caller.dfId(), doc, version, caller.id(), clock.instant());
            return repo.saveIfAbsent(fresh).flatMap(stored -> stored.id().equals(fresh.id())
                    ? audit.record("terms.accepted", caller.dfId(), stored.id(),
                            AuditTrail.details("document", doc.name(), "version", version)).thenReturn(stored)
                    : Mono.just(stored));
        }));
    }

    private Mono<List<Status>> statusOf(String dfId) {
        return repo.findByDfId(dfId).collectList().map(all -> Arrays.stream(TermsDoc.values()).map(doc -> {
            String now = current.get(doc);
            DfTermsAcceptance found = all.stream().filter(a -> a.doc() == doc && a.version().equals(now)).findFirst().orElse(null);
            return new Status(doc, now, found != null, found);
        }).toList());
    }

    private static Mono<Void> member(DfTeamCaller caller) {
        if (caller.platformStaff() || caller.role() == null || caller.dfId() == null) {
            return Mono.error(new DomRuleViolation(403, "not-permitted", "Only people at the fiduciary see or accept these"));
        }
        return Mono.empty();
    }
}
