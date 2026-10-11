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

import org.junit.jupiter.api.Test;

class DfPrflTest {

    @Test
    void aCompleteIndianProfileIsKeptNormalised() {
        DfPrfl prfl = new DfPrfl(" Acme Health Private Limited ", "+91 98765-43210", "https://acme.example",
                "1 MG Road, Bengaluru 560001", "Healthcare", "u72900ka2019ptc123456", "29abcde1234f1z5",
                "Asha Rao", "dpo@acme.example");

        assertEquals("Acme Health Private Limited", prfl.legalName());
        assertEquals("+919876543210", prfl.contactPhone());
        assertEquals("U72900KA2019PTC123456", prfl.cin());
        assertEquals("29ABCDE1234F1Z5", prfl.gstin());
    }

    @Test
    void blankFieldsAreAbsent() {
        DfPrfl prfl = new DfPrfl("", " ", null, "", "", "", "", "", "");

        assertNull(prfl.legalName());
        assertNull(prfl.contactPhone());
        assertNull(prfl.gstin());
        assertEquals(DfPrfl.EMPTY, prfl);
    }

    @Test
    void malformedValuesAreRefusedNamingTheField() {
        assertEquals("contactPhone is not valid", refusal(() -> withPhone("98765 43210")));
        assertEquals("website is not valid", refusal(() -> new DfPrfl(null, null, "acme.example", null, null, null,
                null, null, null)));
        assertEquals("cin is not valid", refusal(() -> new DfPrfl(null, null, null, null, null, "U123", null, null,
                null)));
        assertEquals("gstin is not valid", refusal(() -> new DfPrfl(null, null, null, null, null, null,
                "29ABCDE1234F1X5", null, null)));
        assertEquals("dpoEmail is not valid", refusal(() -> new DfPrfl(null, null, null, null, null, null, null,
                null, "dpo-at-acme")));
        assertEquals("registeredAddress must be at most 500 characters", refusal(() -> new DfPrfl(null, null, null,
                "x".repeat(501), null, null, null, null, null)));
    }

    private static DfPrfl withPhone(String phone) {
        return new DfPrfl(null, phone, null, null, null, null, null, null, null);
    }

    private static String refusal(Runnable build) {
        return assertThrows(IllegalArgumentException.class, build::run).getMessage();
    }
}
