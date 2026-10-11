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

package com.techeazyhq.cm.dfreg.dom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.techeazyhq.cm.domkit.DomainEvt;

class DfTest {

    @Test
    void activationTellsBillingWhichPlanWasChosen() {
        Df df = DfFactory.register("Acme Health", "ops@acme.example", DfPrfl.EMPTY, "STANDARD");

        df.activate("secret");

        List<DomainEvt> evts = df.pullEvts();
        assertEquals(1, evts.size());
        DfActivatedEvt activated = (DfActivatedEvt) evts.get(0);
        assertEquals(df.id(), activated.dfId());
        assertEquals("STANDARD", activated.subPlnName());
    }

    @Test
    void noPlanChosenMeansFree() {
        Df df = DfFactory.register("Acme Health", "ops@acme.example", null, " ");

        assertNull(df.subPlnName());
        assertEquals(DfPrfl.EMPTY, df.prfl());
    }

    @Test
    void registrationNeedsANameAndAValidContactEmail() {
        assertThrows(IllegalArgumentException.class, () -> DfFactory.register(" ", "ops@acme.example", null, null));
        assertEquals("contactEmail is not valid", assertThrows(IllegalArgumentException.class,
                () -> DfFactory.register("Acme", "ops-at-acme", null, null)).getMessage());
        assertEquals("contactEmail must not be blank", assertThrows(IllegalArgumentException.class,
                () -> DfFactory.register("Acme", null, null, null)).getMessage());
    }
}
