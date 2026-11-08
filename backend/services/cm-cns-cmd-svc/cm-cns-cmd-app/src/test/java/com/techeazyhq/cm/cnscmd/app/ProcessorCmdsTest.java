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

package com.techeazyhq.cm.cnscmd.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.techeazyhq.cm.audkit.AuditTrail;
import com.techeazyhq.cm.cnscmd.dom.PrpsVerContent;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Mono;

/** A fiduciary's register of processors, and what a retired one stops purposes from naming (ADR-075). */
class ProcessorCmdsTest {

    private final InMemoryPrpsDefs purposes = new InMemoryPrpsDefs();
    private final InMemoryProcessors register = new InMemoryProcessors();
    private final List<String> audited = new ArrayList<>();
    private final AuditTrail audit = (action, dfId, target, details) ->
            Mono.fromRunnable(() -> audited.add(action + " " + details));
    private final Clock clock = Clock.fixed(InMemoryPrpsDefs.T0, ZoneOffset.UTC);
    private final ProcessorCmds cmds = new ProcessorCmds(register, purposes, audit, clock);
    private final PrpsDefCmds purposeCmds = new PrpsDefCmds(purposes, register, audit, clock);

    private static PrpsVerContent naming(String... recipients) {
        PrpsVerContent c = InMemoryPrpsDefs.orderUpdatesContent();
        return new PrpsVerContent(c.name(), c.description(), c.dCats(), List.of(recipients), List.of(), c.retentionDays(),
                c.requestedValidityDays(), c.maxValidityDays(), Set.of(), c.notices());
    }

    @Test
    void aProcessorIsRegisteredWithItsDetailsTidiedAndAudited() {
        var p = cmds.register("df-1", "  Courier   Ltd ", "Delivers orders", "in", "ops@courier.example").block();

        assertEquals("Courier Ltd", p.name());
        assertEquals("IN", p.country());
        assertTrue(p.active());
        assertEquals(List.of("processor.registered {processor=Courier Ltd}"), audited);
    }

    @Test
    void theSameNameTwiceIgnoringCaseIsRefusedButAnotherFiduciaryMayUseIt() {
        cmds.register("df-1", "Courier Ltd", "", "", "").block();

        var clash = assertThrows(DomRuleViolation.class, () -> cmds.register("df-1", "COURIER ltd", "", "", "").block());
        assertEquals("processor-exists", clash.problemType());
        assertEquals("df-2", cmds.register("df-2", "Courier Ltd", "", "", "").block().dfId());
    }

    @Test
    void badInputIsTheCallersMistake() {
        for (String[] bad : new String[][] {{"", "", "", ""}, {"X", "", "India", ""}, {"X", "", "", "not-an-email"}}) {
            var e = assertThrows(DomRuleViolation.class, () -> cmds.register("df-1", bad[0], bad[1], bad[2], bad[3]).block());
            assertEquals(400, e.httpStatus());
            assertEquals("invalid-processor", e.problemType());
        }
    }

    @Test
    void aProcessorOfAnotherFiduciaryIsNotFound() {
        var p = cmds.register("df-1", "Courier Ltd", "", "", "").block();

        var e = assertThrows(DomRuleViolation.class, () -> cmds.retire("df-2", p.id()).block());
        assertEquals(404, e.httpStatus());
    }

    @Test
    void aRetiredProcessorCannotBeNamedByANewPurpose() {
        var p = cmds.register("df-1", "Courier Ltd", "", "", "").block();
        cmds.retire("df-1", p.id()).block();

        var refused = assertThrows(DomRuleViolation.class,
                () -> purposeCmds.create("df-1", "ORDER_UPDATES", naming("courier ltd")).block());
        assertEquals("processor-retired", refused.problemType());
        assertEquals(409, refused.httpStatus());
        assertEquals(0, purposes.byKey.size());
    }

    @Test
    void aDraftNamingAProcessorRetiredLaterCannotBePublishedButCanBeFixed() {
        var p = cmds.register("df-1", "Courier Ltd", "", "", "").block();
        purposeCmds.create("df-1", "ORDER_UPDATES", naming("Courier Ltd")).block();
        cmds.retire("df-1", p.id()).block();

        var refused = assertThrows(DomRuleViolation.class, () -> purposeCmds.publish("df-1", "ORDER_UPDATES").block());
        assertEquals("processor-retired", refused.problemType());

        purposeCmds.saveDraft("df-1", "ORDER_UPDATES", naming()).block();
        assertEquals(1, purposeCmds.publish("df-1", "ORDER_UPDATES").block().current().orElseThrow().verNo());
    }

    @Test
    void reinstatingAllowsItAgain() {
        var p = cmds.register("df-1", "Courier Ltd", "", "", "").block();
        cmds.retire("df-1", p.id()).block();
        cmds.reinstate("df-1", p.id()).block();

        purposeCmds.create("df-1", "ORDER_UPDATES", naming("Courier Ltd")).block();
        assertTrue(register.byId.get(p.id()).active());
    }

    @Test
    void namesNotInTheRegisterAreStillAcceptedBecauseTheRegisterIsOptIn() {
        cmds.register("df-1", "Courier Ltd", "", "", "").block();

        assertEquals(1, purposeCmds.create("df-1", "ORDER_UPDATES", naming("Somebody Else Inc")).block().versions().size());
    }

    @Test
    void publishingFreezesWhatTheRegisterSaysAboutEachNamedPartyAndLaterEditsDoNotReachIt() {
        var courier = cmds.register("df-1", "Courier Ltd", "Delivers orders", "IN", "").block();
        cmds.register("df-1", "Cloud Host", "Stores order data", "DE", "").block();
        PrpsVerContent draft = new PrpsVerContent("Order updates", null, InMemoryPrpsDefs.orderUpdatesContent().dCats(),
                List.of("courier ltd", "Somebody Unregistered Inc"), List.of("Cloud Host", "COURIER LTD"), 365, 180, 365, Set.of(),
                InMemoryPrpsDefs.orderUpdatesContent().notices());
        purposeCmds.create("df-1", "ORDER_UPDATES", draft).block();
        assertEquals(List.of(), purposeCmds.get("df-1", "ORDER_UPDATES").block().draft().orElseThrow().content().parties(),
                "a draft has no details yet");

        var published = purposeCmds.publish("df-1", "ORDER_UPDATES").block().current().orElseThrow().content();

        assertEquals(List.of(new com.techeazyhq.cm.cnscmd.dom.PrpsParty("courier ltd", "Delivers orders", "IN"),
                new com.techeazyhq.cm.cnscmd.dom.PrpsParty("Cloud Host", "Stores order data", "DE")), published.parties(),
                "the registered parties in the order they are named, once each, spelled as the version spells them; the unregistered one has none");

        cmds.describe("df-1", courier.id(), "Something else entirely", "US", "").block();
        var after = purposeCmds.get("df-1", "ORDER_UPDATES").block().current().orElseThrow().content();
        assertEquals("Delivers orders", after.parties().get(0).role(), "a published version keeps what it was published with");
    }

    @Test
    void aPartyIsDescribedByTheRegisterAsItIsWhenAVersionIsPublishedNotWhenItIsDrafted() {
        var p = cmds.register("df-1", "Courier Ltd", "Old role", "IN", "").block();
        purposeCmds.create("df-1", "ORDER_UPDATES", naming("Courier Ltd")).block();
        cmds.describe("df-1", p.id(), "New role", "IN", "").block();

        var published = purposeCmds.publish("df-1", "ORDER_UPDATES").block().current().orElseThrow().content();

        assertEquals("New role", published.parties().get(0).role());
    }

    @Test
    void usesListsThePurposesThatNameAProcessor() {
        var p = cmds.register("df-1", "Courier Ltd", "", "", "").block();
        var unused = cmds.register("df-1", "Unused Co", "", "", "").block();
        purposeCmds.create("df-1", "ORDER_UPDATES", naming("Courier Ltd")).block();
        purposeCmds.publish("df-1", "ORDER_UPDATES").block();

        var uses = cmds.uses("df-1", p.id()).block();
        assertEquals(1, uses.size());
        assertEquals("ORDER_UPDATES", uses.get(0).purpose());
        assertTrue(cmds.uses("df-1", unused.id()).block().isEmpty());
    }
}
