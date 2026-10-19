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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** The principal chooses which of the asked categories to give, and for how long, within the purpose's bounds (ADR-026). */
class CnsGrantTest {

    private static final Instant NOW = Instant.parse("2026-10-20T00:00:00Z");
    private static final Scp ASKED = new Scp(Set.of(new DCat("EMAIL"), new DCat("NAME")));
    /** Asks for 180 days; the principal may choose up to 365. */
    private static final PrpsVerContent TERMS = new PrpsVerContent("Order updates", null,
            List.of(new PrpsDCat("EMAIL", null, PrpsSensitivity.ORDINARY, true, "No updates without it."),
                    new PrpsDCat("NAME", null, PrpsSensitivity.ORDINARY, false, null)),
            List.of(), List.of(), 365, 180, 365, Set.of(), List.of(new PrpsNtc("en", "T", "X")));

    @Test
    void withNoChoiceEverythingAskedIsGivenForTheRequestedValidity() {
        CnsGrant grant = CnsGrant.of(ASKED, TERMS, null, null, NOW);

        assertEquals(ASKED, grant.scp());
        assertEquals(NOW.plus(Duration.ofDays(180)), grant.valPrd().until());
        assertFalse(grant.isPartialOf(ASKED));
    }

    @Test
    void thePrincipalMayGiveSomeCategoriesIncludingLeavingOutANeededOne() {
        // a needed category can still be refused: the fiduciary may then decline to go ahead (ADR-026 §5)
        CnsGrant grant = CnsGrant.of(ASKED, TERMS, Set.of("NAME"), null, NOW);

        assertEquals(Set.of(new DCat("NAME")), grant.scp().dCats());
        assertTrue(grant.isPartialOf(ASKED));
    }

    @Test
    void thePrincipalMayChooseShorterOrLongerUpToTheMaximum() {
        assertEquals(NOW.plus(Duration.ofDays(30)), CnsGrant.of(ASKED, TERMS, null, 30, NOW).valPrd().until());
        assertEquals(NOW.plus(Duration.ofDays(365)), CnsGrant.of(ASKED, TERMS, null, 365, NOW).valPrd().until());
        assertThrows(IllegalArgumentException.class, () -> CnsGrant.of(ASKED, TERMS, null, 366, NOW));
        assertThrows(IllegalArgumentException.class, () -> CnsGrant.of(ASKED, TERMS, null, 0, NOW));
    }

    @Test
    void nothingAndAnythingNotAskedAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> CnsGrant.of(ASKED, TERMS, Set.of(), null, NOW));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> CnsGrant.of(ASKED, TERMS, Set.of("EMAIL", "LOCATION"), null, NOW));
        assertTrue(e.getMessage().contains("LOCATION"), e.getMessage());
    }

    @Test
    void aRequestWithoutAPurposeVersionKeepsTheOldYear() {
        assertEquals(NOW.plus(Duration.ofDays(365)), CnsGrant.of(ASKED, null, null, null, NOW).valPrd().until());
        assertThrows(IllegalArgumentException.class, () -> CnsGrant.of(ASKED, null, null, 400, NOW));
    }

    @Test
    void onlyTheLinkedPrincipalIsTheRecordsOwner() {
        assertTrue(DpRef.linked("dp-1").isOf("dp-1"));
        assertFalse(DpRef.linked("dp-1").isOf("dp-2"));
        assertFalse(DpRef.linked("dp-1").isOf(null));
        assertFalse(DpRef.unlinked(ExtDpRefTyp.EMAIL, "dp-1@example.com").isOf("dp-1"));
    }
}
