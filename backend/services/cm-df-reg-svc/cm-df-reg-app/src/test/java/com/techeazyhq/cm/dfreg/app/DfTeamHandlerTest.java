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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.techeazyhq.cm.dfreg.dom.Df;
import com.techeazyhq.cm.dfreg.dom.DfFactory;
import com.techeazyhq.cm.dfreg.dom.DfMember;
import com.techeazyhq.cm.dfreg.dom.DfMemberDirectoryPort;
import com.techeazyhq.cm.dfreg.dom.DfMemberExistsException;
import com.techeazyhq.cm.dfreg.dom.DfPrfl;
import com.techeazyhq.cm.dfreg.dom.DfRepoPort;
import com.techeazyhq.cm.dfreg.dom.DfRole;
import com.techeazyhq.cm.dfreg.dom.DfSts;
import com.techeazyhq.cm.dfreg.dom.NewDfMember;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class DfTeamHandlerTest {

    private static final String DF = "df-1";

    private final Map<String, DfMember> directory = new LinkedHashMap<>();
    private final List<String> taken = new ArrayList<>(List.of("operator@cm.local"));
    private final Map<String, Df> dfs = new LinkedHashMap<>();

    private final DfMemberDirectoryPort port = new DfMemberDirectoryPort() {
        @Override
        public Mono<DfMember> invite(String dfId, NewDfMember member) {
            if (taken.contains(member.email())) {
                return Mono.error(new DfMemberExistsException(member.email()));
            }
            taken.add(member.email());
            DfMember created = new DfMember(UUID.randomUUID().toString(), dfId, member.email(), member.name(),
                    member.role());
            directory.put(created.id(), created);
            return Mono.just(created);
        }

        @Override
        public Flux<DfMember> list(String dfId) {
            return Flux.fromIterable(List.copyOf(directory.values())).filter(m -> m.dfId().equals(dfId));
        }

        @Override
        public Mono<DfMember> find(String dfId, String memberId) {
            return Mono.justOrEmpty(directory.get(memberId)).filter(m -> m.dfId().equals(dfId));
        }

        @Override
        public Mono<DfMember> changeRole(DfMember member, DfRole role) {
            DfMember changed = new DfMember(member.id(), member.dfId(), member.email(), member.name(), role);
            directory.put(member.id(), changed);
            return Mono.just(changed);
        }

        @Override
        public Mono<Void> remove(DfMember member) {
            directory.remove(member.id());
            return Mono.empty();
        }
    };

    private final DfRepoPort dfRepo = new DfRepoPort() {
        @Override
        public Mono<Df> save(Df df) {
            dfs.put(df.id(), df);
            return Mono.just(df);
        }

        @Override
        public Mono<Df> findById(String id) {
            return Mono.justOrEmpty(dfs.get(id));
        }

        @Override
        public Flux<Df> findAll() {
            return Flux.fromIterable(dfs.values());
        }
    };

    private final DfTeamHandler team = new DfTeamHandler(port, dfRepo);

    private DfMember seed(String email, DfRole role) {
        return port.invite(DF, new NewDfMember(email, email.substring(0, email.indexOf('@')), role)).block();
    }

    private static DfTeamCaller as(DfMember member) {
        return DfTeamCaller.member(member.id(), member.dfId(), member.role());
    }

    private static void refused(int status, String type, Runnable action) {
        DomRuleViolation refusal = assertThrows(DomRuleViolation.class, action::run);
        assertEquals(status, refusal.httpStatus());
        assertEquals(type, refusal.problemType());
    }

    private void activeDf(String id) {
        dfs.put(id, DfFactory.rehydrate(id, "Acme", "ops@acme.example", DfPrfl.EMPTY, null, DfSts.ACTIVE, Instant.now(),
                "secret", Instant.now()));
    }

    @Test
    void anAdministratorInvitesAConsentManager() {
        DfMember admin = seed("ada@acme.example", DfRole.ADMIN);

        DfMember invited = team.invite(as(admin), DF, " Cy@Acme.example ", "Cy", DfRole.CONSENT_MANAGER).block();

        assertEquals("cy@acme.example", invited.email());
        assertEquals(DfRole.CONSENT_MANAGER, invited.role());
    }

    @Test
    void everyMemberSeesTheTeamOwnersFirst() {
        DfMember auditor = seed("zed@acme.example", DfRole.AUDITOR);
        seed("bo@acme.example", DfRole.OWNER);

        List<DfMember> members = team.list(as(auditor), DF).collectList().block();

        assertEquals(List.of(DfRole.OWNER, DfRole.AUDITOR), members.stream().map(DfMember::role).toList());
    }

    @Test
    void anotherFiduciarysMemberSeesNothing() {
        DfMember outsider = new DfMember("x", "df-2", "x@other.example", "X", DfRole.OWNER);

        refused(403, "not-permitted", () -> team.list(as(outsider), DF).blockLast());
        refused(403, "not-permitted",
                () -> team.invite(as(outsider), DF, "a@b.example", "A", DfRole.AUDITOR).block());
    }

    @Test
    void consentManagersDevelopersAndAuditorsDontManageTheTeam() {
        for (DfRole role : List.of(DfRole.CONSENT_MANAGER, DfRole.DEVELOPER, DfRole.AUDITOR)) {
            DfMember member = seed(role.name().toLowerCase() + "@acme.example", role);
            refused(403, "not-permitted",
                    () -> team.invite(as(member), DF, "n@acme.example", "N", DfRole.AUDITOR).block());
        }
    }

    @Test
    void onlyAnOwnerGrantsOwnership() {
        DfMember admin = seed("ada@acme.example", DfRole.ADMIN);
        DfMember owner = seed("bo@acme.example", DfRole.OWNER);
        DfMember auditor = seed("zed@acme.example", DfRole.AUDITOR);

        refused(403, "not-permitted", () -> team.invite(as(admin), DF, "o@acme.example", "O", DfRole.OWNER).block());
        refused(403, "not-permitted", () -> team.changeRole(as(admin), DF, auditor.id(), DfRole.OWNER).block());
        refused(403, "not-permitted", () -> team.changeRole(as(admin), DF, owner.id(), DfRole.AUDITOR).block());
        refused(403, "not-permitted", () -> team.remove(as(admin), DF, owner.id()).block());

        assertEquals(DfRole.OWNER, team.changeRole(as(owner), DF, auditor.id(), DfRole.OWNER).block().role());
    }

    @Test
    void nobodyChangesOrRemovesThemselves() {
        DfMember owner = seed("bo@acme.example", DfRole.OWNER);
        seed("cy@acme.example", DfRole.OWNER);

        refused(409, "cannot-change-self", () -> team.changeRole(as(owner), DF, owner.id(), DfRole.ADMIN).block());
        refused(409, "cannot-change-self", () -> team.remove(as(owner), DF, owner.id()).block());
    }

    @Test
    void anOwnerRemovesAnotherOwnerAndIsThenTheLastOne() {
        DfMember owner = seed("bo@acme.example", DfRole.OWNER);
        DfMember other = seed("cy@acme.example", DfRole.OWNER);

        team.remove(as(owner), DF, other.id()).block();

        assertEquals(List.of(owner), team.list(as(owner), DF).collectList().block());
        // the last Owner can only be acted on by themselves, which nobody may do
        refused(409, "cannot-change-self", () -> team.changeRole(as(owner), DF, owner.id(), DfRole.ADMIN).block());
    }

    @Test
    void anOwnerWhoLeftMeanwhileDoesntCountAsAnotherOwner() {
        DfMember owner = seed("bo@acme.example", DfRole.OWNER);
        // a token still in use after its Owner was removed elsewhere
        DfTeamCaller removed = DfTeamCaller.member("gone", DF, DfRole.OWNER);

        refused(409, "last-owner", () -> team.remove(removed, DF, owner.id()).block());
    }

    @Test
    void anAddressWithAnAccountIsRefused() {
        DfMember owner = seed("bo@acme.example", DfRole.OWNER);

        refused(409, "account-exists",
                () -> team.invite(as(owner), DF, "operator@cm.local", "Op", DfRole.AUDITOR).block());
    }

    @Test
    void anInvalidInvitationIsA400() {
        DfMember owner = seed("bo@acme.example", DfRole.OWNER);

        refused(400, "invalid-member", () -> team.invite(as(owner), DF, "not-an-email", "N", DfRole.AUDITOR).block());
        refused(400, "invalid-member", () -> team.invite(as(owner), DF, "n@acme.example", "N", null).block());
        refused(400, "invalid-member",
                () -> team.invite(as(owner), DF, "n@acme.example", "Acme & Co", DfRole.AUDITOR).block());
    }

    @Test
    void anUnknownMemberIs404() {
        DfMember owner = seed("bo@acme.example", DfRole.OWNER);

        refused(404, "member-not-found", () -> team.remove(as(owner), DF, "nobody").block());
    }

    @Test
    void staffInviteOnlyTheFirstOwnerOfAnActiveFiduciary() {
        DfTeamCaller staff = DfTeamCaller.staff("stf-1");
        activeDf(DF);

        refused(403, "not-permitted", () -> team.invite(staff, DF, "a@acme.example", "A", DfRole.ADMIN).block());
        DfMember owner = team.invite(staff, DF, "bo@acme.example", "Bo", DfRole.OWNER).block();
        assertEquals(DfRole.OWNER, owner.role());
        refused(409, "owner-exists", () -> team.invite(staff, DF, "cy@acme.example", "Cy", DfRole.OWNER).block());
        refused(403, "not-permitted", () -> team.list(staff, DF).blockLast());
        refused(404, "df-not-found", () -> team.invite(staff, "df-9", "x@acme.example", "X", DfRole.OWNER).block());
    }

    @Test
    void staffCantInviteForAFiduciaryNotYetActive() {
        dfs.put("df-p", DfFactory.rehydrate("df-p", "Acme", "ops@acme.example", DfPrfl.EMPTY, null, DfSts.PENDING,
                Instant.now(), null, null));

        refused(409, "df-not-active",
                () -> team.invite(DfTeamCaller.staff("stf-1"), "df-p", "bo@acme.example", "Bo", DfRole.OWNER).block());
        assertTrue(directory.isEmpty());
    }
}
