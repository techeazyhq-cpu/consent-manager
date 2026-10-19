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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.techeazyhq.cm.cnscmd.dom.CnsReqBatch.Input;
import com.techeazyhq.cm.cnscmd.dom.CnsReqBatch.Outcome;

class CnsReqBatchTest {

    private static final CnsReqTemplate TEMPLATE =
            CnsReqTemplate.of(new Prps("ORDER_UPDATES"), new Scp(Set.of(new DCat("EMAIL"))), "Order updates",
                    "We email you about your orders.", new PrpsVerRef("ver-1", 1, "en", "hash"));

    private static CnsReqBatch.Plan plan(Input... inputs) {
        return CnsReqBatch.plan("df-1", TEMPLATE, CnsReqBatch.Source.CSV, "list.csv", "u-1", List.of(inputs));
    }

    @Test
    void recognisesEmailAddressesAndMobileNumbersAsWritten() {
        assertEquals(new Recipient(ExtDpRefTyp.EMAIL, "asha@example.com"),
                ((Recipient.Ok) Recipient.parse("  Asha@Example.COM ", null)).recipient());
        assertEquals(new Recipient(ExtDpRefTyp.PHONE, "+919876543210"),
                ((Recipient.Ok) Recipient.parse("+91 98765-43210", null)).recipient());
        assertEquals(new Recipient(ExtDpRefTyp.PHONE, "+919876543210"),
                ((Recipient.Ok) Recipient.parse("+91 (98765) 432.10", null)).recipient());
        assertEquals("Add the country code, for example +91 98765 43210",
                ((Recipient.Problem) Recipient.parse("098765 43210", null)).reason(), "texts need the country code");
        assertEquals("Not an email address or mobile number",
                ((Recipient.Problem) Recipient.parse("not-an-address", null)).reason());
        assertInstanceOf(Recipient.Problem.class, Recipient.parse("asha@", null));
        assertInstanceOf(Recipient.Problem.class, Recipient.parse("12345", null));
        assertInstanceOf(Recipient.Problem.class, Recipient.parse("", null));
        assertInstanceOf(Recipient.Problem.class, Recipient.parse("+919876543210", ExtDpRefTyp.EMAIL),
                "a stated type is held to");
    }

    @Test
    void sendsValidRowsOnceAndSaysWhyTheRestWont() {
        CnsReqBatch.Plan plan = plan(
                new Input("asha@example.com", "cust-1", null),
                new Input("not an address", "cust-2", null),
                new Input("ASHA@example.com", "cust-3", null),
                new Input("+91 98765 43210", null, null));

        CnsReqBatch batch = plan.batch();
        assertEquals(4, batch.total());
        assertEquals(2, batch.valid());
        assertEquals(1, batch.invalid());
        assertEquals(1, batch.duplicate());
        assertEquals(CnsReqBatch.Sts.QUEUED, batch.sts());
        assertEquals(List.of(Outcome.PENDING, Outcome.INVALID, Outcome.DUPLICATE, Outcome.PENDING),
                plan.rows().stream().map(CnsReqBatch.Row::outcome).toList());
        assertEquals(List.of(2, 3), plan.problems().stream().map(CnsReqBatch.Row::rowNo).toList());
        assertEquals("cust-1", plan.rows().get(0).ref());
    }

    @Test
    void theSandboxTakesOnlyTestContacts() {
        ContactPolicy sandbox = ContactPolicy.TEST_CONTACTS_ONLY;
        assertEquals(java.util.Optional.empty(), sandbox.refusal(new Recipient(ExtDpRefTyp.EMAIL, "asha@sandbox.example")));
        assertEquals(java.util.Optional.empty(), sandbox.refusal(new Recipient(ExtDpRefTyp.EMAIL, "a@team.sandbox.example")));
        assertEquals(java.util.Optional.empty(), sandbox.refusal(new Recipient(ExtDpRefTyp.PHONE, "+447700900123")));
        assertEquals(java.util.Optional.of(ContactPolicy.REFUSED),
                sandbox.refusal(new Recipient(ExtDpRefTyp.EMAIL, "asha@example.com")));
        assertEquals(java.util.Optional.of(ContactPolicy.REFUSED),
                sandbox.refusal(new Recipient(ExtDpRefTyp.EMAIL, "asha@notsandbox.example")));
        assertEquals(java.util.Optional.of(ContactPolicy.REFUSED),
                sandbox.refusal(new Recipient(ExtDpRefTyp.PHONE, "+919876543210")));
        assertEquals(java.util.Optional.empty(), ContactPolicy.ANYONE.refusal(new Recipient(ExtDpRefTyp.PHONE, "+919876543210")));

        CnsReqBatch.Plan plan = CnsReqBatch.plan("df-1", TEMPLATE, CnsReqBatch.Source.CSV, "list.csv", "u-1",
                List.of(new Input("asha@sandbox.example", null, null), new Input("asha@example.com", null, null)),
                sandbox);
        assertEquals(1, plan.batch().valid());
        assertEquals(ContactPolicy.REFUSED, plan.problems().get(0).problem());
    }

    @Test
    void aListWithNothingToSendIsDoneAtOnce() {
        CnsReqBatch.Plan plan = plan(new Input("nope", null, null));

        assertEquals(CnsReqBatch.Sts.DONE, plan.batch().sts());
        assertEquals(0, plan.batch().valid());
    }

    @Test
    void refusesAnEmptyListAndOneLongerThanABatch() {
        assertThrows(IllegalArgumentException.class, () -> plan());
        List<Input> tooMany = new ArrayList<>();
        for (int i = 0; i <= CnsReqBatch.MAX_ROWS; i++) {
            tooMany.add(new Input("p" + i + "@example.com", null, null));
        }
        assertThrows(IllegalArgumentException.class, () -> plan(tooMany.toArray(Input[]::new)));
    }

    @Test
    void finishingGivesBackWhatWasNotSent() {
        CnsReqBatch batch = plan(new Input("a@example.com", null, null), new Input("b@example.com", null, null),
                new Input("c@example.com", null, null)).batch();

        batch.progressed(2, 1);

        assertEquals(true, batch.allRowsDone());
        assertEquals(1, batch.finish(), "one reserved request was not used");
        assertEquals(CnsReqBatch.Sts.DONE, batch.sts());
    }

    @Test
    void theTemplateNeedsANoticePeopleCanRead() {
        Scp email = new Scp(Set.of(new DCat("EMAIL")));
        PrpsVerRef v1 = new PrpsVerRef("ver-1", 1, "en", "hash");
        assertThrows(IllegalArgumentException.class, () -> CnsReqTemplate.of(new Prps("P"), email, "Title", " ", v1));
        assertThrows(IllegalArgumentException.class, () -> CnsReqTemplate.of(new Prps("P"), email, " ", "Text", v1));
    }
}
