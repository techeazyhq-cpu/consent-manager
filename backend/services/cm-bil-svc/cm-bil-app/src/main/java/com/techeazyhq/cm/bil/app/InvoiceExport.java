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

package com.techeazyhq.cm.bil.app;

import java.util.List;

import com.techeazyhq.cm.bil.dom.Invoice;

/**
 * Invoices as the accounts see them (ADR-079): a CSV with a row per invoice or credit note, amounts in major units with two
 * decimals, and a printable HTML page for one. Everything written into HTML is escaped; a CSV cell that could read as a formula
 * is prefixed with an apostrophe.
 */
public final class InvoiceExport {

    private InvoiceExport() {
    }

    /** Minor units as a decimal amount: 1250 is {@code 12.50}, -50 is {@code -0.50}. */
    public static String money(long minor) {
        String sign = minor < 0 ? "-" : "";
        long abs = Math.abs(minor);
        return sign + abs / 100 + "." + (abs % 100 < 10 ? "0" : "") + abs % 100;
    }

    public static String csv(List<Invoice> invoices) {
        StringBuilder out = new StringBuilder("number,kind,fiduciary,period_from,period_to,currency,subtotal,tax_label,tax_rate_percent,"
                + "tax,total,status,issued_at,paid_at,credits_number\n");
        for (Invoice i : invoices) {
            out.append(cell(i.number())).append(',').append(i.kind()).append(',').append(cell(i.dfId())).append(',')
                    .append(i.periodFrom()).append(',').append(i.periodTo()).append(',').append(i.currency()).append(',')
                    .append(money(i.subtotalMinor())).append(',').append(cell(i.taxLabel())).append(',')
                    .append(money(i.taxBps())).append(',').append(money(i.taxMinor())).append(',').append(money(i.totalMinor()))
                    .append(',').append(i.sts()).append(',').append(i.issuedAt()).append(',')
                    .append(i.paidAt() == null ? "" : i.paidAt()).append(',').append(cell(i.creditsInvoiceId())).append('\n');
        }
        return out.toString();
    }

    static String cell(String value) {
        String v = value == null ? "" : value;
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        if (v.indexOf(',') >= 0 || v.indexOf('"') >= 0 || v.indexOf('\n') >= 0 || v.indexOf('\r') >= 0) {
            v = '"' + v.replace("\"", "\"\"") + '"';
        }
        return v;
    }

    public static String html(Invoice i, String dfName) {
        StringBuilder rows = new StringBuilder();
        for (Invoice.Line l : i.lines()) {
            rows.append("<tr><td>").append(esc(l.description())).append("</td><td class=\"n\">")
                    .append(esc(money(l.amountMinor()))).append("</td></tr>");
        }
        String title = (i.kind() == Invoice.Kind.CREDIT_NOTE ? "Credit note " : "Invoice ") + esc(i.number());
        return "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\"><title>" + title + "</title><style>"
                + "body{font:14px system-ui,sans-serif;margin:2rem auto;max-width:44rem;color:#111}"
                + "table{width:100%;border-collapse:collapse}td,th{padding:.4rem .2rem;border-bottom:1px solid #ccc;text-align:left}"
                + ".n{text-align:right}.t td{font-weight:600}</style></head><body><h1>" + title + "</h1>"
                + "<p>" + esc(dfName == null ? i.dfId() : dfName) + "<br>Period: " + i.periodFrom().toString().substring(0, 10)
                + " to " + i.periodTo().minusSeconds(1).toString().substring(0, 10) + " (UTC)<br>Issued: "
                + i.issuedAt().toString().substring(0, 10) + " &middot; Status: " + i.sts()
                + (i.creditsInvoiceId() != null ? "<br>Reason: " + esc(i.reason()) : "") + "</p>"
                + "<table><thead><tr><th>Description</th><th class=\"n\">Amount (" + esc(i.currency()) + ")</th></tr></thead><tbody>"
                + rows + "<tr><td>Subtotal</td><td class=\"n\">" + money(i.subtotalMinor()) + "</td></tr>"
                + "<tr><td>" + esc(i.taxLabel()) + " " + money(i.taxBps()) + "%</td><td class=\"n\">" + money(i.taxMinor()) + "</td></tr>"
                + "<tr class=\"t\"><td>Total</td><td class=\"n\">" + money(i.totalMinor()) + "</td></tr></tbody></table></body></html>";
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
