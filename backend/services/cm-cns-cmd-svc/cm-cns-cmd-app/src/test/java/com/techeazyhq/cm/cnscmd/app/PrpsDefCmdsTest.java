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
import com.techeazyhq.cm.cnscmd.dom.CnsReqTemplate;
import com.techeazyhq.cm.cnscmd.dom.DCat;
import com.techeazyhq.cm.cnscmd.dom.PrpsChange;
import com.techeazyhq.cm.cnscmd.dom.PrpsDCat;
import com.techeazyhq.cm.cnscmd.dom.PrpsDef;
import com.techeazyhq.cm.cnscmd.dom.PrpsSensitivity;
import com.techeazyhq.cm.cnscmd.dom.PrpsVerContent;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Mono;

/** Purposes are defined, drafted and published by fiduciaries; requests resolve only the current version (ADR-031). */
class PrpsDefCmdsTest {

    private final InMemoryPrpsDefs repo = new InMemoryPrpsDefs();
    private final List<String> audited = new ArrayList<>();
    private final AuditTrail audit = (action, dfId, target, details) ->
            Mono.fromRunnable(() -> audited.add(action + " " + target + " " + details));
    private final PrpsDefCmds cmds = new PrpsDefCmds(repo, audit, Clock.fixed(InMemoryPrpsDefs.T0, ZoneOffset.UTC));
    private final PrpsResolver resolver = new PrpsResolver(repo);

    private static PrpsVerContent withPhone() {
        PrpsVerContent c = InMemoryPrpsDefs.orderUpdatesContent();
        List<PrpsDCat> dCats = new ArrayList<>(c.dCats());
        dCats.add(new PrpsDCat("PHONE", null, PrpsSensitivity.ORDINARY, false, null));
        return new PrpsVerContent(c.name(), c.description(), dCats, c.recipients(), c.transfers(), c.retentionDays(),
                c.requestedValidityDays(), c.maxValidityDays(), c.flags(), c.notices());
    }

    @Test
    void aPurposeIsDefinedThenPublishedAndEveryStepIsAudited() {
        cmds.create("df-1", "ORDER_UPDATES", InMemoryPrpsDefs.orderUpdatesContent()).block();
        PrpsDef def = cmds.publish("df-1", "ORDER_UPDATES").block();

        assertEquals(1, def.current().orElseThrow().verNo());
        assertEquals(List.of("purpose.created ORDER_UPDATES {purpose=ORDER_UPDATES}",
                "purpose.published ORDER_UPDATES {purpose=ORDER_UPDATES, version=1, change=INITIAL}"), audited);
        assertEquals(1, repo.events.size(), "the publication is an event");
    }

    @Test
    void aSecondPurposeWithTheSameCodeIsRefused() {
        repo.orderUpdates();
        DomRuleViolation e = assertThrows(DomRuleViolation.class,
                () -> cmds.create("df-1", "ORDER_UPDATES", InMemoryPrpsDefs.orderUpdatesContent()).block());
        assertEquals(409, e.httpStatus());
    }

    @Test
    void anInvalidDraftIsTheCallersMistake() {
        repo.orderUpdates();
        PrpsVerContent noNotice = new PrpsVerContent("Order updates", null,
                InMemoryPrpsDefs.orderUpdatesContent().dCats(), List.of(), List.of(), 365, 180, 365, Set.of(), List.of());
        DomRuleViolation e = assertThrows(DomRuleViolation.class,
                () -> cmds.saveDraft("df-1", "ORDER_UPDATES", noNotice).block());
        assertEquals(400, e.httpStatus());
        assertThrows(DomRuleViolation.class, () -> cmds.publish("df-1", "ORDER_UPDATES").block(), "no draft");
    }

    @Test
    void requestsResolveTheCurrentVersionAndAreToldWhenTheirsIsSuperseded() {
        repo.orderUpdates();
        cmds.saveDraft("df-1", "ORDER_UPDATES", withPhone()).block();

        CnsReqTemplate v1 = resolver.resolve("df-1", "ORDER_UPDATES", 1, null, Set.of()).block();
        assertEquals(Set.of(new DCat("EMAIL"), new DCat("NAME")), v1.scp().dCats(), "the draft isn't in use yet");
        assertEquals(404, refusal("ORDER_UPDATES", 2, Set.of()).httpStatus(), "a draft can't be asked for");

        PrpsDef def = cmds.publish("df-1", "ORDER_UPDATES").block();
        assertEquals(PrpsChange.Kind.MATERIAL, def.current().orElseThrow().change().kind());

        DomRuleViolation superseded = refusal("ORDER_UPDATES", 1, Set.of());
        assertEquals(409, superseded.httpStatus());
        assertTrue(superseded.getMessage().contains("superseded by version 2"), superseded.getMessage());
        assertEquals(3, resolver.resolve("df-1", "ORDER_UPDATES", 2, "en", Set.of()).block().scp().dCats().size());
    }

    @Test
    void aRequestIsRefusedForAnUnknownPurposeOrDataThePurposeDoesntCover() {
        repo.orderUpdates();
        assertEquals(404, refusal("NEWSLETTER", 1, Set.of()).httpStatus());
        assertEquals(400, refusal("ORDER_UPDATES", 1, Set.of("LOCATION")).httpStatus());
        assertEquals(400, refusal("ORDER_UPDATES", null, Set.of()).httpStatus(), "the version must be named");
        assertTrue(resolver.resolve("df-2", "ORDER_UPDATES", 1, null, Set.of()).onErrorResume(e -> Mono.empty())
                .blockOptional().isEmpty(), "another fiduciary's purpose isn't found");
    }

    private DomRuleViolation refusal(String code, Integer ver, Set<String> dCats) {
        return assertThrows(DomRuleViolation.class, () -> resolver.resolve("df-1", code, ver, null, dCats).block());
    }
}
