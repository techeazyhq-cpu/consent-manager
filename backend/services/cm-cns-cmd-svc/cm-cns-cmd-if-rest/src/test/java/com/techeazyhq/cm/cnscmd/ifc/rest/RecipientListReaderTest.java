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

package com.techeazyhq.cm.cnscmd.ifc.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.techeazyhq.cm.cnscmd.dom.CnsReqBatch;
import com.techeazyhq.cm.cnscmd.dom.CnsReqBatch.Input;
import com.techeazyhq.cm.cnscmd.dom.ExtDpRefTyp;

/** ADR-036: lists the way small businesses keep them. */
class RecipientListReaderTest {

    private static List<Input> csv(String text) {
        return RecipientListReader.read(CnsReqBatch.Source.CSV, text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void readsAHeaderedListWithAReferenceColumn() {
        assertEquals(List.of(new Input("asha@example.com", "C-1", ExtDpRefTyp.EMAIL),
                        new Input("ravi@example.com", "C-2", ExtDpRefTyp.EMAIL)),
                csv("Customer ID,Email\nC-1,asha@example.com\nC-2,ravi@example.com\n"));
    }

    @Test
    void usesTheEmailWhenThereIsOneAndTheMobileOtherwise() {
        assertEquals(List.of(new Input("asha@example.com", null, ExtDpRefTyp.EMAIL),
                        new Input("+91 98765 43210", null, ExtDpRefTyp.PHONE)),
                csv("Email,Mobile Number\r\nasha@example.com,+91 98765 43210\r\n,+91 98765 43210\r\n"));
    }

    @Test
    void readsAListWithoutAHeader() {
        assertEquals(List.of(new Input("asha@example.com", "C-1", null), new Input("+919876543210", null, null)),
                csv("asha@example.com,C-1\n+919876543210\n"));
    }

    @Test
    void handlesSemicolonsQuotesABomAndBlankLines() {
        assertEquals(List.of(new Input("asha@example.com", "Shah, Asha", ExtDpRefTyp.EMAIL)),
                csv("﻿email;reference\n\nasha@example.com;\"Shah, Asha\"\n;\n"));
    }

    @Test
    void refusesAHeaderWithNoContactColumnAndAnEmptyFile() {
        assertThrows(IllegalArgumentException.class, () -> csv("Name,Customer ID\nAsha,C-1\n"));
        assertThrows(IllegalArgumentException.class, () -> csv(""));
        assertThrows(IllegalArgumentException.class, () -> csv("\n\n"));
    }

    @Test
    void stopsReadingJustPastTheBatchLimitSoTheBatchCanRefuseIt() {
        StringBuilder big = new StringBuilder("email\n");
        for (int i = 0; i < CnsReqBatch.MAX_ROWS + 50; i++) {
            big.append("p").append(i).append("@example.com\n");
        }
        assertEquals(CnsReqBatch.MAX_ROWS + 1, csv(big.toString()).size());
    }

    @Test
    void knowsCsvAndExcelAndRefusesOtherFiles() {
        assertEquals(CnsReqBatch.Source.CSV, RecipientListReader.sourceOf("list.CSV", null));
        assertEquals(CnsReqBatch.Source.XLSX, RecipientListReader.sourceOf("list.xlsx", null));
        assertThrows(IllegalArgumentException.class, () -> RecipientListReader.sourceOf("list.pdf", "application/pdf"));
    }

    @Test
    void theReportNeverHandsASpreadsheetAFormula() {
        assertEquals("\"'=HYPERLINK(\"\"x\"\")\"", CnsReqBatchHttpHandler.csv("=HYPERLINK(\"x\")"));
        assertEquals("\"Shah, Asha\"", CnsReqBatchHttpHandler.csv("Shah, Asha"));
        assertEquals("'+91", CnsReqBatchHttpHandler.csv("+91"));
        assertEquals("", CnsReqBatchHttpHandler.csv(null));
    }
}
