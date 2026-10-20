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

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** After giving consent, the principal may end it earlier, withdraw a category, or renew it before it lapses (ADR-026 §4). */
class CnsArtChangeTest {

    private static final Instant GIVEN = Instant.parse("2026-10-01T00:00:00Z");
    private static final Instant NOW = GIVEN.plus(Duration.ofDays(10));

    /** EMAIL and NAME, given for 30 days. */
    private static CnsArt given() {
        return CnsArtFactory.rehydrate("art-1", "req-1", "df-1", DpRef.linked("dp-1"), new Prps("ORDER_UPDATES"),
                new Scp(Set.of(new DCat("EMAIL"), new DCat("NAME"))), ValPrd.of(GIVEN, Duration.ofDays(30)),
                CnsArtSts.ISSUED, GIVEN, null);
    }

    @Test
    void shorteningEndsItEarlierAndVerificationFollowsAtOnce() {
        CnsArt art = given();
        Instant newEnd = NOW.plus(Duration.ofDays(2));

        art.shortenTo(newEnd, NOW);

        assertEquals(newEnd, art.valPrd().until());
        assertEquals(CnsArtSts.ISSUED, art.liveStatus(newEnd.minusSeconds(1)));
        assertEquals(CnsArtSts.EXPIRED, art.liveStatus(newEnd.plusSeconds(1)), "it stops verifying at the new end");
        CnsModifiedEvt evt = assertInstanceOf(CnsModifiedEvt.class, art.pullEvts().get(0));
        assertEquals(CnsModifiedEvt.Change.SHORTENED, evt.change());
        assertEquals(newEnd, evt.valPrd().until());
    }

    @Test
    void shorteningCantExtendOrEndInThePast() {
        CnsArt art = given();
        assertThrows(IllegalArgumentException.class, () -> art.shortenTo(art.valPrd().until().plusSeconds(1), NOW));
        assertThrows(IllegalArgumentException.class, () -> art.shortenTo(art.valPrd().until(), NOW));
        assertThrows(IllegalArgumentException.class, () -> art.shortenTo(NOW, NOW));
    }

    @Test
    void withdrawingACategoryKeepsTheRest() {
        CnsArt art = given();

        art.withdrawCategory(new DCat("NAME"), NOW);

        assertEquals(Set.of(new DCat("EMAIL")), art.scp().dCats());
        CnsModifiedEvt evt = assertInstanceOf(CnsModifiedEvt.class, art.pullEvts().get(0));
        assertEquals(CnsModifiedEvt.Change.CATEGORY_WITHDRAWN, evt.change());
        assertEquals("NAME", evt.dCat());
        assertEquals(Set.of(new DCat("EMAIL")), evt.scp().dCats());
    }

    @Test
    void theLastCategoryIsWithdrawnByWithdrawingTheConsent() {
        CnsArt art = given();
        art.withdrawCategory(new DCat("NAME"), NOW);
        assertThrows(IllegalArgumentException.class, () -> art.withdrawCategory(new DCat("EMAIL"), NOW));
        assertThrows(IllegalArgumentException.class, () -> art.withdrawCategory(new DCat("PHONE"), NOW));
    }

    @Test
    void renewingStartsANewValidityUpToTheMaximumThatEndsLater() {
        CnsArt art = given();

        art.renew(60, 90, NOW);

        assertEquals(NOW, art.valPrd().from());
        assertEquals(NOW.plus(Duration.ofDays(60)), art.valPrd().until());
        CnsRenewedEvt evt = assertInstanceOf(CnsRenewedEvt.class, art.pullEvts().get(0));
        assertEquals(60, evt.validityDays());

        assertThrows(IllegalArgumentException.class, () -> given().renew(91, 90, NOW), "past the maximum");
        assertThrows(IllegalArgumentException.class, () -> given().renew(5, 90, NOW), "ends sooner: that's shortening");
    }

    @Test
    void aLapsedOrWithdrawnConsentCantBeChanged() {
        Instant afterLapse = GIVEN.plus(Duration.ofDays(31));
        assertThrows(IllegalStateException.class, () -> given().renew(30, 90, afterLapse), "a lapsed consent is asked again");
        assertThrows(IllegalStateException.class, () -> given().withdrawCategory(new DCat("NAME"), afterLapse));

        CnsArt withdrawn = given();
        withdrawn.revoke();
        assertThrows(IllegalStateException.class, () -> withdrawn.shortenTo(NOW.plusSeconds(60), NOW));
    }

    @Test
    void aLapseIsRecordedWithItsFiduciaryRequestAndTime() {
        CnsArt art = given();
        Instant afterLapse = GIVEN.plus(Duration.ofDays(31));

        art.expire(afterLapse);

        CnsExpiredEvt evt = assertInstanceOf(CnsExpiredEvt.class, art.pullEvts().get(0));
        assertEquals("req-1", evt.cnsReqId());
        assertEquals("df-1", evt.dfId());
        assertEquals(GIVEN.plus(Duration.ofDays(30)), evt.lapsedAt());
    }
}
