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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

/** A new request keeps to the sizes it can be stored and explained in (VAPT INP-07, INP-08). */
class CnsReqFactoryTest {

    private static final DpRef ASHA = new DpRef.UnlnkDpRef(ExtDpRefTyp.EMAIL, "asha@example.com");
    private static final Scp EMAIL = new Scp(Set.of(new DCat("EMAIL")));
    private static final PrpsVerRef V1 = new PrpsVerRef("ver-1", 1, "en", "hash");

    @Test
    void anOrdinaryRequestIsMade() {
        assertDoesNotThrow(() -> create(new Prps("ORDER_UPDATES"), EMAIL, "Order updates",
                "We email you when your order ships."));
    }

    @Test
    void oversizedPartsAreRefused() {
        Scp fiftyOne = new Scp(IntStream.range(0, 51).mapToObj(i -> new DCat("C" + i)).collect(Collectors.toSet()));
        assertThrows(IllegalArgumentException.class, () -> create(new Prps("P".repeat(65)), EMAIL, "Title", "Text"));
        assertThrows(IllegalArgumentException.class, () -> create(new Prps("P"), fiftyOne, "Title", "Text"));
        assertThrows(IllegalArgumentException.class,
                () -> create(new Prps("P"), new Scp(Set.of(new DCat("D".repeat(65)))), "Title", "Text"));
        assertThrows(IllegalArgumentException.class, () -> create(new Prps("P"), EMAIL, "T".repeat(256), "Text"));
        assertThrows(IllegalArgumentException.class, () -> create(new Prps("P"), EMAIL, "Title", "x".repeat(20_001)));
    }

    @Test
    void aBatchTemplateIsCheckedTheSameWay() {
        assertThrows(IllegalArgumentException.class, () -> CnsReqTemplate.of(new Prps("ORDER_UPDATES"),
                new Scp(IntStream.range(0, 51).mapToObj(i -> new DCat("C" + i)).collect(Collectors.toSet())), "Title",
                "Text", V1));
    }

    @Test
    void controlCharactersAreRefusedButANoticeKeepsItsLineBreaks() {
        assertThrows(IllegalArgumentException.class, () -> create(new Prps("P"), EMAIL, "Title\u0000", "Text"));
        assertThrows(IllegalArgumentException.class, () -> create(new Prps("P"), EMAIL, "Title", "Te\u0000xt"));
        assertThrows(IllegalArgumentException.class,
                () -> create(new Prps("P"), new Scp(Set.of(new DCat("EM\u0000AIL"))), "Title", "Text"));
        assertThrows(IllegalArgumentException.class, () -> CnsReqFactory.create("df-1",
                new DpRef.UnlnkDpRef(ExtDpRefTyp.EMAIL, "a".repeat(250) + "@example.com"),
                CnsReqTemplate.of(new Prps("P"), EMAIL, "Title", "Text", V1)));
        assertDoesNotThrow(() -> create(new Prps("P"), EMAIL, "Title", "First line.\nSecond line.\tTabbed."));
        assertInstanceOf(Recipient.Problem.class, Recipient.parse("as\u0000ha@example.com", ExtDpRefTyp.EMAIL));
    }

    @Test
    void aNoticeThatIsBlankOnceTrimmedIsRefused() {
        // \u0001 isn't whitespace to isBlank: it's caught as a control character
        assertThrows(IllegalArgumentException.class,
                () -> CnsReqTemplate.of(new Prps("ORDER_UPDATES"), EMAIL, "Title", "\u0001", V1));
    }

    @Test
    void aStoredTemplateIsReadBackEvenIfTodayItWouldBeRefused() {
        assertDoesNotThrow(() -> new CnsReqTemplate(new Prps("ORDER_UPDATES"), EMAIL, "Title", "", null));
    }

    @Test
    void aRequestNamesAPurposeVersion() {
        assertThrows(IllegalArgumentException.class, () -> CnsReqTemplate.of(new Prps("P"), EMAIL, "Title", "Text", null));
        assertThrows(IllegalArgumentException.class, () -> CnsReqFactory.create("df-1", ASHA,
                new CnsReqTemplate(new Prps("P"), EMAIL, "Title", "Text", null)));
    }

    @Test
    void storedRequestsRehydrateWhateverTheirSize() {
        Scp many = new Scp(IntStream.range(0, 500).mapToObj(i -> new DCat("C" + i)).collect(Collectors.toSet()));
        assertDoesNotThrow(() -> CnsReqFactory.rehydrate("id", "df-1", ASHA,
                new CnsReqTemplate(new Prps("P"), many, "Title", "Text", V1), CnsReqSts.PENDING, java.time.Instant.EPOCH));
    }

    private static CnsReq create(Prps prps, Scp scp, String title, String text) {
        return CnsReqFactory.create("df-1", ASHA, CnsReqTemplate.of(prps, scp, title, text, V1));
    }
}
