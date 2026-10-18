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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.Row;
import org.dhatim.fastexcel.reader.Sheet;

import com.techeazyhq.cm.cnscmd.dom.CnsReqBatch;
import com.techeazyhq.cm.cnscmd.dom.ExtDpRefTyp;
import com.techeazyhq.cm.cnscmd.dom.Recipient;

/**
 * Reads a list of people from a CSV or Excel ({@code .xlsx}) file, the way small businesses keep them (ADR-036).
 * <ul>
 *   <li>A header row is optional. Columns are found by name: {@code email}, {@code mobile}/{@code phone}, or
 *       {@code contact}; and {@code ref}/{@code reference}/{@code customer id} for the fiduciary's own reference.
 *       Without a header, the first column is the contact and the second the reference.</li>
 *   <li>With both an email and a mobile column, a row's email is used, or its mobile if it has no email.</li>
 *   <li>Blank lines are skipped. Reading stops one row past the batch limit, which the batch then refuses.</li>
 * </ul>
 */
public final class RecipientListReader {

    public static final long MAX_FILE_BYTES = 5L * 1024 * 1024;

    private static final Set<String> EMAIL_HEADERS = Set.of("email", "emailaddress", "mail", "emailid");
    private static final Set<String> PHONE_HEADERS =
            Set.of("mobile", "mobilenumber", "mobileno", "phone", "phonenumber", "phoneno", "cell", "msisdn");
    private static final Set<String> CONTACT_HEADERS = Set.of("contact", "recipient", "to", "emailormobile");
    private static final Set<String> REF_HEADERS =
            Set.of("ref", "reference", "yourref", "customerid", "customer", "externalid", "id", "accountid");

    private RecipientListReader() {
    }

    public static CnsReqBatch.Source sourceOf(String fileName, String contentType) {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (name.endsWith(".xlsx") || type.contains("spreadsheetml")) {
            return CnsReqBatch.Source.XLSX;
        }
        if (name.endsWith(".csv") || name.endsWith(".txt") || type.contains("csv") || type.startsWith("text/")) {
            return CnsReqBatch.Source.CSV;
        }
        throw new IllegalArgumentException("The list must be a CSV file or an Excel workbook (.xlsx)");
    }

    public static List<CnsReqBatch.Input> read(CnsReqBatch.Source source, byte[] bytes) {
        if (bytes.length == 0) {
            throw new IllegalArgumentException("The file is empty");
        }
        if (bytes.length > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("The file is larger than 5 MB; split the list");
        }
        List<List<String>> table = source == CnsReqBatch.Source.XLSX ? readXlsx(bytes) : readCsv(bytes);
        return toInputs(table);
    }

    /** Turns a table of cells into rows of the list, finding the columns from a header if there is one. */
    static List<CnsReqBatch.Input> toInputs(List<List<String>> table) {
        List<List<String>> rows = table.stream().filter(r -> r.stream().anyMatch(c -> c != null && !c.isBlank())).toList();
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("The file has no rows");
        }
        Columns cols = Columns.fromHeader(rows.get(0));
        List<List<String>> body = cols == null ? rows : rows.subList(1, rows.size());
        Columns use = cols == null ? new Columns(-1, -1, 0, 1) : cols;
        if (cols != null && use.email < 0 && use.phone < 0 && use.contact < 0) {
            throw new IllegalArgumentException("No email, mobile or contact column found in the header row");
        }
        List<CnsReqBatch.Input> inputs = new ArrayList<>();
        for (List<String> row : body) {
            if (inputs.size() > CnsReqBatch.MAX_ROWS) {
                break;
            }
            inputs.add(use.input(row));
        }
        return inputs;
    }

    private record Columns(int email, int phone, int contact, int ref) {

        static Columns fromHeader(List<String> header) {
            int email = -1;
            int phone = -1;
            int contact = -1;
            int ref = -1;
            for (int i = 0; i < header.size(); i++) {
                String h = header.get(i) == null ? "" : header.get(i).toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
                if (email < 0 && EMAIL_HEADERS.contains(h)) {
                    email = i;
                } else if (phone < 0 && PHONE_HEADERS.contains(h)) {
                    phone = i;
                } else if (contact < 0 && CONTACT_HEADERS.contains(h)) {
                    contact = i;
                } else if (ref < 0 && REF_HEADERS.contains(h)) {
                    ref = i;
                }
            }
            boolean named = email >= 0 || phone >= 0 || contact >= 0 || ref >= 0;
            // a first row that is itself a contact is data, not a header
            boolean firstCellIsContact = !header.isEmpty()
                    && Recipient.parse(header.get(0), null) instanceof Recipient.Ok;
            return named && !firstCellIsContact ? new Columns(email, phone, contact, ref) : null;
        }

        CnsReqBatch.Input input(List<String> row) {
            String refVal = cell(row, ref);
            String emailVal = cell(row, email);
            if (emailVal != null && !emailVal.isBlank()) {
                return new CnsReqBatch.Input(emailVal, refVal, ExtDpRefTyp.EMAIL);
            }
            String phoneVal = cell(row, phone);
            if (phoneVal != null && !phoneVal.isBlank()) {
                return new CnsReqBatch.Input(phoneVal, refVal, ExtDpRefTyp.PHONE);
            }
            return new CnsReqBatch.Input(cell(row, contact), refVal, null);
        }

        private static String cell(List<String> row, int i) {
            return i < 0 || i >= row.size() ? null : row.get(i);
        }
    }

    /** RFC 4180 CSV: quoted fields, doubled quotes, newlines inside quotes; comma, semicolon or tab separated. */
    static List<List<String>> readCsv(byte[] bytes) {
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        char sep = separatorOf(text);
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    field.append(c);
                }
            } else if (c == '"' && field.length() == 0) {
                quoted = true;
            } else if (c == sep) {
                row.add(field.toString());
                field.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                row.add(field.toString());
                field.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
                if (rows.size() > CnsReqBatch.MAX_ROWS + 1) {
                    return rows;
                }
            } else {
                field.append(c);
            }
        }
        if (field.length() > 0 || !row.isEmpty()) {
            row.add(field.toString());
            rows.add(row);
        }
        return rows;
    }

    private static char separatorOf(String text) {
        int end = text.indexOf('\n');
        String first = end < 0 ? text : text.substring(0, end);
        long commas = first.chars().filter(ch -> ch == ',').count();
        long semis = first.chars().filter(ch -> ch == ';').count();
        long tabs = first.chars().filter(ch -> ch == '\t').count();
        return tabs > commas && tabs > semis ? '\t' : semis > commas ? ';' : ',';
    }

    /** The first sheet of an Excel workbook, every cell as text. */
    static List<List<String>> readXlsx(byte[] bytes) {
        try (ReadableWorkbook workbook = new ReadableWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getFirstSheet();
            List<List<String>> rows = new ArrayList<>();
            try (Stream<Row> stream = sheet.openStream()) {
                stream.limit(CnsReqBatch.MAX_ROWS + 2L).forEach(r -> {
                    List<String> cells = new ArrayList<>();
                    for (int i = 0; i < r.getCellCount(); i++) {
                        cells.add(r.getCellText(i));
                    }
                    rows.add(cells);
                });
            }
            return rows;
        } catch (IOException | RuntimeException e) {
            throw new IllegalArgumentException("The Excel file couldn't be read; save it as .xlsx and try again");
        }
    }
}
