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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/** A fiduciary's description must not carry anyone's details to a model (ADR-111). */
class PersonalDataScreenTest {

    @Test
    void ordinaryDescriptionsOfAPurposeAreLeftAlone() {
        assertTrue(PersonalDataScreen.find("We send order updates by email and SMS so customers know when their parcel ships. "
                + "We keep the email address and the phone number for 90 days after delivery.").isEmpty());
        assertTrue(PersonalDataScreen.find("Retention is 365 days; consent lasts 2 years; we have about 12000 customers in 2026.").isEmpty());
        assertTrue(PersonalDataScreen.find(null).isEmpty());
    }

    @Test
    void anEmailAddressIsFound() {
        assertEquals(List.of("an email address"), PersonalDataScreen.find("Contact asha.rao@example.com about the order"));
    }

    @Test
    void aPhoneNumberIsFoundInTheUsualWrittenForms() {
        assertEquals(List.of("a phone number"), PersonalDataScreen.find("call +91 98765 43210 for details"));
        assertEquals(List.of("a phone number"), PersonalDataScreen.find("call 98765-43210 for details"));
        assertEquals(List.of("a phone number"), PersonalDataScreen.find("(080) 4123 4567"));
    }

    @Test
    void aPanIsFound() {
        assertEquals(List.of("a PAN"), PersonalDataScreen.find("the customer's PAN is ABCDE1234F"));
    }

    @Test
    void anAadhaarShapedNumberIsFoundWhetherGroupedOrNot() {
        assertEquals(List.of("an Aadhaar-shaped number"), PersonalDataScreen.find("Aadhaar 2345 6789 0123 was shown"));
        assertEquals(List.of("an Aadhaar-shaped number"), PersonalDataScreen.find("234567890123"));
    }

    @Test
    void aRealCardNumberIsFoundButADigitRunThatFailsTheLuhnCheckIsNotACard() {
        // 4111 1111 1111 1111 is the well-known test Visa number and passes the Luhn check
        assertTrue(PersonalDataScreen.find("pays with 4111 1111 1111 1111").contains("a card number"));
        // sixteen digits that are not a card: not reported as one (it may still look like a phone number)
        assertTrue(!PersonalDataScreen.find("order reference 1234 5678 9012 3456").contains("a card number"));
    }

    @Test
    void severalKindsAreEachNamedAndTheTextIsNeverRepeated() {
        List<String> kinds = PersonalDataScreen.find("Asha, asha@example.com, PAN ABCDE1234F");
        assertEquals(List.of("an email address", "a PAN"), kinds);
        assertTrue(kinds.stream().noneMatch(k -> k.contains("asha") || k.contains("ABCDE")));
    }
}
