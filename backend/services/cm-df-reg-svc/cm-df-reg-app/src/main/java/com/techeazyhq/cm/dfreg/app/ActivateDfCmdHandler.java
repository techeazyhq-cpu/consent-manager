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

import org.springframework.transaction.annotation.Transactional;

import com.techeazyhq.cm.audkit.AuditTrail;
import com.techeazyhq.cm.dfreg.dom.Df;
import com.techeazyhq.cm.dfreg.dom.DfApiClientProvisionPort;
import com.techeazyhq.cm.dfreg.dom.DfMemberDirectoryPort;
import com.techeazyhq.cm.dfreg.dom.DfRepoPort;
import com.techeazyhq.cm.dfreg.dom.DfRole;
import com.techeazyhq.cm.dfreg.dom.NewDfMember;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Mono;

/**
 * Activation provisions the {@code cm-df-api} client secret before flipping the aggregate to ACTIVE (plan §10), then
 * invites the contact address as the fiduciary's first Owner (ADR-033), who signs in to the DF Console as themselves.
 * The invitation is best effort: the fiduciary is active either way, and platform staff can invite the Owner later.
 */
public class ActivateDfCmdHandler implements CmdHandler<ActivateDfCmd, Df> {

    private static final System.Logger LOG = System.getLogger(ActivateDfCmdHandler.class.getName());

    private final DfRepoPort dfRepo;
    private final DfApiClientProvisionPort provisionPort;
    private final DfMemberDirectoryPort members;
    private final AuditTrail audit;

    public ActivateDfCmdHandler(DfRepoPort dfRepo, DfApiClientProvisionPort provisionPort,
                                DfMemberDirectoryPort members) {
        this(dfRepo, provisionPort, members, AuditTrail.NONE);
    }

    public ActivateDfCmdHandler(DfRepoPort dfRepo, DfApiClientProvisionPort provisionPort,
                                DfMemberDirectoryPort members, AuditTrail audit) {
        this.dfRepo = dfRepo;
        this.provisionPort = provisionPort;
        this.members = members;
        this.audit = audit;
    }

    @Override
    @Transactional
    public Mono<Df> handle(ActivateDfCmd cmd) {
        return dfRepo.findById(cmd.dfId())
                .switchIfEmpty(Mono.error(new DomRuleViolation(404, "df-not-found", "No DF " + cmd.dfId())))
                .flatMap(df -> provisionPort.provisionClientSecret(df.id())
                        .doOnNext(df::activate)
                        // Mono.defer, not a bare dfRepo.save(df) call: the adapter reads df's
                        // fields (sts, cmDfApiClientSecret) to build the SQL bind values the
                        // moment save() is *invoked*, not when the returned Mono is
                        // *subscribed* — without defer, that invocation happens while
                        // constructing this flatMap's return value, before doOnNext(df::activate)
                        // has actually run, so it silently persisted the PENDING/no-secret
                        // state. Found via Postgres inspection during Phase 1 verification:
                        // the REST response showed ACTIVE, but the DB row stayed PENDING.
                        .then(Mono.defer(() -> dfRepo.save(df))))
                .flatMap(active -> audit.record("tenant.activated", active.id(), active.id(),
                        AuditTrail.details("apiClient", "df-api-" + active.id())).thenReturn(active))
                .flatMap(active -> inviteFirstOwner(active).thenReturn(active));
    }

    private Mono<Void> inviteFirstOwner(Df df) {
        return members.list(df.id()).hasElements()
                .flatMap(hasMembers -> hasMembers ? Mono.<Void>empty() : members.invite(df.id(),
                        new NewDfMember(df.contactEmail(), ownerName(df), DfRole.OWNER)).then())
                .onErrorResume(e -> {
                    LOG.log(System.Logger.Level.WARNING, "DF {0} is active but its first Owner was not invited: {1}",
                            df.id(), e.getMessage());
                    return Mono.empty();
                });
    }

    private static String ownerName(Df df) {
        return NewDfMember.nameFrom(df.prfl().legalName() != null ? df.prfl().legalName() : df.name(), "Owner");
    }
}
