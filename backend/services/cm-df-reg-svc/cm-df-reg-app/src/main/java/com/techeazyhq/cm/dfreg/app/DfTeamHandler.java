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

import java.util.Comparator;

import com.techeazyhq.cm.audkit.AuditTrail;
import com.techeazyhq.cm.dfreg.dom.DfMember;
import com.techeazyhq.cm.dfreg.dom.DfMemberDirectoryPort;
import com.techeazyhq.cm.dfreg.dom.DfMemberExistsException;
import com.techeazyhq.cm.dfreg.dom.DfRepoPort;
import com.techeazyhq.cm.dfreg.dom.DfRole;
import com.techeazyhq.cm.dfreg.dom.DfSts;
import com.techeazyhq.cm.dfreg.dom.NewDfMember;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * A fiduciary's team (ADR-033). The rules:
 * <ul>
 *   <li>every member sees the team; Owners and Administrators invite, change roles and remove;</li>
 *   <li>only an Owner grants, changes or removes the Owner role;</li>
 *   <li>nobody changes or removes themselves, and the last Owner can't be removed or demoted;</li>
 *   <li>an address that already has an account of any kind is refused (409), never turned into a member;</li>
 *   <li>platform staff may only invite the Owner of an active fiduciary that has none.</li>
 * </ul>
 */
public class DfTeamHandler {

    private final DfMemberDirectoryPort directory;
    private final DfRepoPort dfRepo;
    private final AuditTrail audit;

    public DfTeamHandler(DfMemberDirectoryPort directory, DfRepoPort dfRepo) {
        this(directory, dfRepo, AuditTrail.NONE);
    }

    public DfTeamHandler(DfMemberDirectoryPort directory, DfRepoPort dfRepo, AuditTrail audit) {
        this.directory = directory;
        this.dfRepo = dfRepo;
        this.audit = audit;
    }

    /** Owners first, then by role and name. */
    public Flux<DfMember> list(DfTeamCaller caller, String dfId) {
        return Flux.defer(() -> {
            if (!caller.isMemberOf(dfId)) {
                return Flux.error(forbidden("Only members of a fiduciary see its team"));
            }
            return directory.list(dfId)
                    .sort(Comparator.comparing(DfMember::role).thenComparing(DfMember::name, String.CASE_INSENSITIVE_ORDER));
        });
    }

    public Mono<DfMember> invite(DfTeamCaller caller, String dfId, String email, String name, DfRole role) {
        return Mono.fromCallable(() -> new NewDfMember(email, name, role))
                .onErrorMap(IllegalArgumentException.class, e -> new DomRuleViolation(400, "invalid-member", e.getMessage()))
                .flatMap(member -> requireMayInvite(caller, dfId, member).thenReturn(member))
                .flatMap(member -> directory.invite(dfId, member))
                .flatMap(invited -> audit.record("member.invited", dfId, invited.id(),
                        AuditTrail.details("member", invited.email(), "role", invited.role().name())).thenReturn(invited))
                .onErrorMap(DfMemberExistsException.class, e -> new DomRuleViolation(409, "account-exists", e.getMessage()));
    }

    public Mono<DfMember> changeRole(DfTeamCaller caller, String dfId, String memberId, DfRole newRole) {
        if (newRole == null) {
            return Mono.error(new DomRuleViolation(400, "invalid-member", "role is required"));
        }
        return requireManager(caller, dfId)
                .then(Mono.defer(() -> findMember(dfId, memberId)))
                .flatMap(member -> {
                    if (member.id().equals(caller.id())) {
                        return Mono.error(new DomRuleViolation(409, "cannot-change-self", "You cannot change your own role"));
                    }
                    boolean touchesOwner = member.role() == DfRole.OWNER || newRole == DfRole.OWNER;
                    if (touchesOwner && caller.role() != DfRole.OWNER) {
                        return Mono.error(forbidden("Only an Owner grants or changes the Owner role"));
                    }
                    Mono<Void> lastOwnerCheck = member.role() == DfRole.OWNER && newRole != DfRole.OWNER
                            ? requireAnotherOwner(dfId, member) : Mono.empty();
                    return lastOwnerCheck.then(Mono.defer(() -> directory.changeRole(member, newRole)))
                            .flatMap(changed -> audit.record("member.role-changed", dfId, member.id(),
                                    AuditTrail.details("member", member.email(), "from", member.role().name(),
                                            "to", newRole.name())).thenReturn(changed));
                });
    }

    public Mono<Void> remove(DfTeamCaller caller, String dfId, String memberId) {
        return requireManager(caller, dfId)
                .then(Mono.defer(() -> findMember(dfId, memberId)))
                .flatMap(member -> {
                    if (member.id().equals(caller.id())) {
                        return Mono.error(new DomRuleViolation(409, "cannot-change-self", "You cannot remove yourself"));
                    }
                    if (member.role() == DfRole.OWNER && caller.role() != DfRole.OWNER) {
                        return Mono.error(forbidden("Only an Owner removes an Owner"));
                    }
                    Mono<Void> lastOwnerCheck = member.role() == DfRole.OWNER ? requireAnotherOwner(dfId, member) : Mono.empty();
                    return lastOwnerCheck.then(Mono.defer(() -> directory.remove(member)))
                            .then(audit.record("member.removed", dfId, member.id(),
                                    AuditTrail.details("member", member.email(), "role", member.role().name())));
                });
    }

    private Mono<Void> requireMayInvite(DfTeamCaller caller, String dfId, NewDfMember member) {
        if (caller.platformStaff()) {
            if (member.role() != DfRole.OWNER) {
                return Mono.error(forbidden("Platform staff only invite a fiduciary's Owner"));
            }
            return dfRepo.findById(dfId)
                    .switchIfEmpty(Mono.error(new DomRuleViolation(404, "df-not-found", "No DF " + dfId)))
                    .flatMap(df -> df.sts() != DfSts.ACTIVE
                            ? Mono.error(new DomRuleViolation(409, "df-not-active", "Activate the fiduciary first"))
                            : directory.list(dfId).filter(m -> m.role() == DfRole.OWNER).hasElements()
                                    .flatMap(hasOwner -> hasOwner
                                            ? Mono.<Void>error(new DomRuleViolation(409, "owner-exists",
                                                    "The fiduciary already has an Owner, who manages its team"))
                                            : Mono.<Void>empty()));
        }
        if (member.role() == DfRole.OWNER && caller.isMemberOf(dfId) && caller.role() != DfRole.OWNER) {
            return Mono.error(forbidden("Only an Owner grants the Owner role"));
        }
        return requireManager(caller, dfId);
    }

    private Mono<Void> requireManager(DfTeamCaller caller, String dfId) {
        if (!caller.isMemberOf(dfId) || !caller.role().managesTeam()) {
            return Mono.error(forbidden("Only an Owner or an Administrator manages the team"));
        }
        return Mono.empty();
    }

    private Mono<DfMember> findMember(String dfId, String memberId) {
        return directory.find(dfId, memberId)
                .switchIfEmpty(Mono.error(new DomRuleViolation(404, "member-not-found", "No member " + memberId)));
    }

    private Mono<Void> requireAnotherOwner(String dfId, DfMember leaving) {
        return directory.list(dfId)
                .filter(m -> m.role() == DfRole.OWNER && !m.id().equals(leaving.id()))
                .hasElements()
                .flatMap(another -> another ? Mono.<Void>empty()
                        : Mono.error(new DomRuleViolation(409, "last-owner", "A fiduciary must keep at least one Owner")));
    }

    private static DomRuleViolation forbidden(String message) {
        return new DomRuleViolation(403, "not-permitted", message);
    }
}
