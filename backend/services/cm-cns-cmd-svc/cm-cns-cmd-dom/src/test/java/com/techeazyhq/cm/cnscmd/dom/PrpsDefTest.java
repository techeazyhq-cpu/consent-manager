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

package com.techeazyhq.cm.cnscmd.dom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** Purposes are versioned, classified by the platform, and requests may ask only for what a version covers (ADR-031). */
class PrpsDefTest {

    private static final Instant T0 = Instant.parse("2026-10-01T00:00:00Z");
    private static final PrpsDCat EMAIL = new PrpsDCat("EMAIL", "dpv-pd:EmailAddress", PrpsSensitivity.ORDINARY,
            true, "Without your email we can't send order updates.");
    private static final PrpsDCat NAME = new PrpsDCat("NAME", null, PrpsSensitivity.ORDINARY, false, null);
    private static final PrpsNtc EN = new PrpsNtc("en", "Order updates", "We email you when your order ships.");

    static PrpsVerContent content(List<PrpsDCat> dCats, List<String> recipients, int retention, int requested,
                                  int max, Set<PrpsFlag> flags, List<PrpsNtc> notices) {
        return new PrpsVerContent("Order updates", "Emails about orders", dCats, recipients, List.of(), retention,
                requested, max, flags, notices);
    }

    static PrpsVerContent base() {
        return content(List.of(EMAIL, NAME), List.of("Courier Ltd"), 365, 180, 365, Set.of(), List.of(EN));
    }

    private static PrpsDef published(PrpsVerContent content) {
        PrpsDef def = PrpsDef.create("df-1", "ORDER_UPDATES", content, T0);
        def.publish(T0);
        def.pullEvts();
        return def;
    }

    @Test
    void theFirstVersionIsADraftUntilPublished() {
        PrpsDef def = PrpsDef.create("df-1", "ORDER_UPDATES", base(), T0);
        assertTrue(def.current().isEmpty());
        assertEquals(1, def.draft().orElseThrow().verNo());

        PrpsVer v1 = def.publish(T0);

        assertEquals(PrpsChange.Kind.INITIAL, v1.change().kind());
        assertEquals(v1, def.current().orElseThrow());
        assertTrue(def.draft().isEmpty());
        PrpsVerPublishedEvt evt = assertInstanceOf(PrpsVerPublishedEvt.class, def.pullEvts().get(0));
        assertEquals(1, evt.verNo());
    }

    @Test
    void aPublishedVersionNeverChangesAndEditsGoToTheNextVersion() {
        PrpsDef def = published(base());
        PrpsVer v1 = def.current().orElseThrow();

        PrpsVer draft = def.saveDraft(content(List.of(EMAIL), List.of("Courier Ltd"), 365, 180, 365, Set.of(),
                List.of(EN)), T0);

        assertEquals(2, draft.verNo());
        assertEquals(v1, def.version(1).orElseThrow(), "version 1 is untouched");
        assertEquals(v1, def.current().orElseThrow(), "requests still use version 1 until 2 is published");
    }

    @Test
    void moreDataNewRecipientsLongerRetentionOrValidityAndRaisedSensitivityAreMaterial() {
        PrpsVerContent before = base();
        PrpsDCat phone = new PrpsDCat("PHONE", null, PrpsSensitivity.ORDINARY, false, null);
        PrpsDCat sensitiveName = new PrpsDCat("NAME", null, PrpsSensitivity.SENSITIVE, false, null);

        assertMaterial(before, content(List.of(EMAIL, NAME, phone), List.of("Courier Ltd"), 365, 180, 365, Set.of(),
                List.of(EN)), "Asks for more data: PHONE");
        assertMaterial(before, content(List.of(EMAIL, NAME), List.of("Courier Ltd", "Ad Network"), 365, 180, 365,
                Set.of(), List.of(EN)), "New recipient: Ad Network");
        assertMaterial(before, content(List.of(EMAIL, NAME), List.of("Courier Ltd"), 400, 180, 365, Set.of(),
                List.of(EN)), "Keeps data longer: 400 days, was 365");
        assertMaterial(before, content(List.of(EMAIL, NAME), List.of("Courier Ltd"), 365, 180, 730, Set.of(),
                List.of(EN)), "Consent may last longer: up to 730 days, was 365");
        assertMaterial(before, content(List.of(EMAIL, sensitiveName), List.of("Courier Ltd"), 365, 180, 365, Set.of(),
                List.of(EN)), "NAME is now SENSITIVE, was ORDINARY");
        assertMaterial(before, content(List.of(EMAIL, NAME), List.of("Courier Ltd"), 365, 180, 365,
                Set.of(PrpsFlag.TRACKING), List.of(EN)), "New kind of processing: TRACKING");
    }

    @Test
    void lessDataShorterRetentionAndNewWordingAreNotMaterial() {
        PrpsVerContent after = content(List.of(EMAIL), List.of(), 200, 90, 300, Set.of(),
                List.of(new PrpsNtc("en", "Order updates", "We email you when your order ships, and not otherwise."),
                        new PrpsNtc("hi", "ऑर्डर अपडेट", "आपका ऑर्डर भेजे जाने पर हम आपको ईमेल करते हैं।")));

        PrpsChange change = PrpsChange.between(base(), after);

        assertEquals(PrpsChange.Kind.NON_MATERIAL, change.kind(), change.reasons().toString());
        assertTrue(change.minor().containsAll(List.of("No longer asks for NAME", "No longer a recipient: Courier Ltd",
                "Keeps data for less time: 200 days, was 365", "The en notice is worded differently",
                "A notice in hi")), change.minor().toString());
    }

    @Test
    void publishingAMaterialChangeSaysWhy() {
        PrpsDef def = published(base());
        def.saveDraft(content(List.of(EMAIL, NAME), List.of("Courier Ltd"), 365, 180, 365, Set.of(PrpsFlag.TRACKING),
                List.of(EN)), T0);

        PrpsVer v2 = def.publish(T0);

        assertEquals(PrpsChange.Kind.MATERIAL, v2.change().kind());
        PrpsVerPublishedEvt evt = (PrpsVerPublishedEvt) def.pullEvts().get(0);
        assertEquals(List.of("New kind of processing: TRACKING"), evt.reasons());
    }

    @Test
    void aDraftIdenticalToTheCurrentVersionCantBePublished() {
        PrpsDef def = published(base());
        def.saveDraft(base(), T0);
        assertThrows(IllegalArgumentException.class, () -> def.publish(T0));
    }

    @Test
    void aDraftCanBeDiscarded() {
        PrpsDef def = published(base());
        def.saveDraft(content(List.of(EMAIL), List.of(), 365, 180, 365, Set.of(), List.of(EN)), T0);
        def.discardDraft();
        assertTrue(def.draft().isEmpty());
        assertEquals(1, def.versions().size());
    }

    @Test
    void aRequestAsksForTheVersionsCategoriesInTheNoticesLanguage() {
        PrpsDef def = published(base());

        CnsReqTemplate all = def.templateFor(1, null, null);
        CnsReqTemplate some = def.templateFor(1, "en", Set.of("EMAIL"));

        assertEquals(Set.of(new DCat("EMAIL"), new DCat("NAME")), all.scp().dCats());
        assertEquals(Set.of(new DCat("EMAIL")), some.scp().dCats());
        assertEquals("Order updates", some.ntcTitle());
        assertEquals(new PrpsVerRef(def.current().orElseThrow().id(), 1, "en", EN.hash()), some.prpsVer());
    }

    @Test
    void aRequestCantAskForDataThePurposeDoesntCover() {
        PrpsDef def = published(base());
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> def.templateFor(1, null, Set.of("EMAIL", "LOCATION")));
        assertTrue(e.getMessage().contains("LOCATION"), e.getMessage());
    }

    @Test
    void aRequestNeedsANoticeInTheLanguageItNamesAndAPublishedVersion() {
        PrpsDef def = published(base());
        assertThrows(IllegalArgumentException.class, () -> def.templateFor(1, "ta", null));
        def.saveDraft(content(List.of(EMAIL), List.of(), 365, 180, 365, Set.of(), List.of(EN)), T0);
        assertThrows(IllegalArgumentException.class, () -> def.templateFor(2, null, null), "a draft can't be asked");
    }

    @Test
    void aNewDraftIsChecked() {
        PrpsDCat necessaryWithoutConsequence = new PrpsDCat("EMAIL", null, PrpsSensitivity.ORDINARY, true, " ");
        List<PrpsVerContent> bad = new ArrayList<>(List.of(
                content(List.of(), List.of(), 365, 180, 365, Set.of(), List.of(EN)),
                content(List.of(necessaryWithoutConsequence), List.of(), 365, 180, 365, Set.of(), List.of(EN)),
                content(List.of(EMAIL, EMAIL), List.of(), 365, 180, 365, Set.of(), List.of(EN)),
                content(List.of(new PrpsDCat("e-mail", null, null, false, null)), List.of(), 365, 180, 365, Set.of(),
                        List.of(EN)),
                content(List.of(EMAIL), List.of(), 365, 400, 365, Set.of(), List.of(EN)),
                content(List.of(EMAIL), List.of(), 365, 180, PrpsVerContent.MAX_VALIDITY_DAYS + 1, Set.of(),
                        List.of(EN)),
                content(List.of(EMAIL), List.of(), 0, 180, 365, Set.of(), List.of(EN)),
                content(List.of(EMAIL), List.of(), 365, 180, 365, Set.of(), List.of()),
                content(List.of(EMAIL), List.of(), 365, 180, 365, Set.of(), List.of(EN, EN)),
                content(List.of(EMAIL), List.of(), 365, 180, 365, Set.of(),
                        List.of(new PrpsNtc("English", "Title", "Text"))),
                content(List.of(EMAIL), List.of(), 365, 180, 365, Set.of(),
                        List.of(new PrpsNtc("en", "Title\u0000", "Text"))),
                content(List.of(EMAIL), List.of("A", "A"), 365, 180, 365, Set.of(), List.of(EN))));
        for (PrpsVerContent c : bad) {
            assertThrows(IllegalArgumentException.class, () -> PrpsDef.create("df-1", "ORDER_UPDATES", c, T0),
                    c.toString());
        }
        assertThrows(IllegalArgumentException.class, () -> PrpsDef.create("df-1", "order updates", base(), T0));
    }

    @Test
    void theNoticeHashIsStable() {
        // the V3 migration computes the same for requests sent before notices were versioned
        // (encode(sha256(convert_to('Title' || E'\n\n' || 'Text', 'UTF8')), 'hex') in PostgreSQL)
        assertEquals("7f5c213bec75e1dcb2ef78eb9c8f78aeefbe56272dbeb9cd533d685419fe75b1",
                new PrpsNtc("en", "Title", "Text").hash());
    }

    private static void assertMaterial(PrpsVerContent before, PrpsVerContent after, String reason) {
        PrpsChange change = PrpsChange.between(before, after);
        assertEquals(PrpsChange.Kind.MATERIAL, change.kind(), change.reasons().toString());
        assertTrue(change.material().contains(reason), change.material().toString());
    }
}
