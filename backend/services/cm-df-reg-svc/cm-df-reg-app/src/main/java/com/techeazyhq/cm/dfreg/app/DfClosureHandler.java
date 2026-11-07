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
import java.time.Instant;

import org.springframework.transaction.annotation.Transactional;

import com.techeazyhq.cm.audkit.AuditTrail;
import com.techeazyhq.cm.dfreg.dom.ConsentsClosurePort;
import com.techeazyhq.cm.dfreg.dom.Df;
import com.techeazyhq.cm.dfreg.dom.DfApiKeyRepoPort;
import com.techeazyhq.cm.dfreg.dom.DfLogoRepoPort;
import com.techeazyhq.cm.dfreg.dom.DfMemberDirectoryPort;
import com.techeazyhq.cm.dfreg.dom.DfRepoPort;
import com.techeazyhq.cm.dfreg.dom.DfSts;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Closing a fiduciary whose wind-down has ended (ADR-064). The pass is {@link #cleanUp} (everything that can be undone by
 * running it again: end the consents, revoke the keys, remove the team and the logo) and then {@link #finish} (mark it
 * CLOSED, erase the profile, audit). A failure anywhere leaves the fiduciary LEAVING, and the next pass starts again.
 *
 * <p>Kept, deliberately: the consent records under their pseudonymous ids, the purposes, the audit ledger, the legal name
 * and registration numbers, the invoices and the acceptances of the terms (ADR-063). Not yet done: the purge of what has
 * passed its retention, the Keycloak API client, and the ledger's payloads about the team.
 */
public class DfClosureHandler {

    /** What the clean-up did, for the audit entry. */
    public record Summary(long consentsEnded, long requestsDeclined, long contactsErased, long keysRevoked, long membersRemoved) {
    }

    private final DfRepoPort dfs;
    private final ConsentsClosurePort consents;
    private final DfApiKeyRepoPort keys;
    private final DfMemberDirectoryPort members;
    private final DfLogoRepoPort logos;
    private final AuditTrail audit;
    private final Clock clock;

    public DfClosureHandler(DfRepoPort dfs, ConsentsClosurePort consents, DfApiKeyRepoPort keys,
                            DfMemberDirectoryPort members, DfLogoRepoPort logos, AuditTrail audit, Clock clock) {
        this.dfs = dfs;
        this.consents = consents;
        this.keys = keys;
        this.members = members;
        this.logos = logos;
        this.audit = audit;
        this.clock = clock;
    }

    /** The fiduciaries whose wind-down has ended. */
    public Flux<String> due() {
        Instant now = clock.instant();
        return dfs.findAll().filter(df -> df.sts() == DfSts.LEAVING && df.leavesAt() != null && !df.leavesAt().isAfter(now))
                .map(Df::id);
    }

    public Mono<Summary> cleanUp(String dfId) {
        return consents.closeConsents(dfId).flatMap(ended -> revokeKeys(dfId)
                .flatMap(revoked -> removeMembers(dfId)
                        .flatMap(removed -> logos.deleteByDfId(dfId)
                                .thenReturn(new Summary(ended.revoked(), ended.declined(), ended.contactsErased(), revoked, removed)))));
    }

    @Transactional
    public Mono<Df> finish(String dfId, Summary summary) {
        return dfs.findById(dfId).filter(df -> df.sts() == DfSts.LEAVING).flatMap(df -> {
            df.close(clock.instant());
            return dfs.save(df).flatMap(saved -> audit.record("tenant.closed", dfId, dfId, AuditTrail.details(
                    "consentsEnded", String.valueOf(summary.consentsEnded()),
                    "requestsDeclined", String.valueOf(summary.requestsDeclined()),
                    "contactsErased", String.valueOf(summary.contactsErased()),
                    "keysRevoked", String.valueOf(summary.keysRevoked()),
                    "membersRemoved", String.valueOf(summary.membersRemoved()))).thenReturn(saved));
        });
    }

    private Mono<Long> revokeKeys(String dfId) {
        return keys.findByDfId(dfId).filter(key -> !key.isRevoked()).concatMap(key -> {
            key.revoke();
            return keys.save(key);
        }).count();
    }

    private Mono<Long> removeMembers(String dfId) {
        return members.list(dfId).collectList().flatMapMany(Flux::fromIterable)
                .concatMap(member -> members.remove(member).thenReturn(member)).count();
    }
}
