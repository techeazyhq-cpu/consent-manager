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

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.techeazyhq.cm.cnscmd.dom.CnsRec;
import com.techeazyhq.cm.cnscmd.dom.CnsRecEvt;
import com.techeazyhq.cm.cnscmd.dom.PrpsDCat;
import com.techeazyhq.cm.cnscmd.dom.PrpsNtc;
import com.techeazyhq.cm.cnscmd.dom.PrpsVer;
import com.techeazyhq.cm.cnscmd.dom.PrpsVerContent;

/**
 * A consent record as an ISO/IEC TS 27560 document in JSON-LD with W3C DPV (ADR-025): Header, Notice, Processing,
 * Parties and Events. Rendered from the record and the purpose version it names; both only ever grow or never change,
 * so the same record renders the same document. The domain stays neutral: this is one mapping at the boundary.
 */
public final class CnsRecDoc {

    public static final String SCHEMA = "cm-27560/1";

    /** What the platform says about itself in every record: who it is, where, and how to complain (ADR-023). */
    public record Platform(String name, String jurisdiction, String grievanceContact, String withdrawalUrl) {
    }

    private CnsRecDoc() {
    }

    /**
     * @param ver            the purpose version the request names; null only for a request stored without one
     * @param controllerName the fiduciary's registered name; its id stands in when unknown
     */
    public static Map<String, Object> render(CnsRec rec, PrpsVer ver, String controllerName, Platform platform) {
        PrpsVerContent content = ver == null ? null : ver.content();
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("@context", Map.of(
                "dpv", "https://w3id.org/dpv#",
                "pd", "https://w3id.org/dpv/pd#",
                "cm", "urn:cm:"));
        doc.put("@type", "dpv:ConsentRecord");
        doc.put("@id", "urn:cm:record:" + rec.id());

        Map<String, Object> header = new LinkedHashMap<>();
        header.put("schemaVersion", SCHEMA);
        header.put("recordId", rec.id());
        header.put("consentRequest", rec.cnsReqId());
        header.put("createdAt", rec.createdAt().toString());
        header.put("subject", rec.dpId() == null ? null : "urn:cm:dp:" + rec.dpId());
        doc.put("header", header);

        Map<String, Object> notice = new LinkedHashMap<>();
        if (rec.prpsVer() != null) {
            notice.put("@id", "urn:cm:notice:" + rec.prpsVer().ntcHash());
            notice.put("language", rec.prpsVer().ntcLang());
            notice.put("hash", "sha256:" + rec.prpsVer().ntcHash());
            notice.put("purposeVersion", "urn:cm:prps-ver:" + rec.prpsVer().prpsVerId());
            notice.put("version", rec.prpsVer().verNo());
            PrpsNtc text = content == null ? null : content.notice(rec.prpsVer().ntcLang()).orElse(null);
            if (text != null && text.hash().equals(rec.prpsVer().ntcHash())) {
                notice.put("title", text.title());
                notice.put("text", text.text());
            }
        }
        doc.put("notice", notice);

        Map<String, Object> controller = new LinkedHashMap<>();
        controller.put("@id", "urn:cm:df:" + rec.dfId());
        controller.put("@type", "dpv:DataController");
        controller.put("name", controllerName == null ? rec.dfId() : controllerName);

        Map<String, Object> processing = new LinkedHashMap<>();
        Map<String, Object> purpose = new LinkedHashMap<>();
        purpose.put("@id", "urn:cm:purpose:" + rec.dfId() + ":" + rec.prpsCode());
        purpose.put("code", rec.prpsCode());
        if (content != null) {
            purpose.put("name", content.name());
            purpose.put("description", content.description());
        }
        processing.put("purposes", List.of(purpose));
        processing.put("personalData", content == null ? List.of() : content.dCats().stream().map(CnsRecDoc::dCat).toList());
        processing.put("legalBasis", "dpv:Consent");
        processing.put("controller", controller);
        processing.put("recipients", content == null ? List.of() : content.recipients());
        processing.put("transfers", content == null ? List.of() : content.transfers());
        processing.put("storageDuration", content == null ? null : days(content.retentionDays()));
        processing.put("requestedValidity", content == null ? null
                : days(content.requestedValidityDays()));
        processing.put("maximumValidity", content == null ? null : days(content.maxValidityDays()));
        processing.put("jurisdictions", List.of(platform.jurisdiction()));
        processing.put("withdrawal", Map.of(
                "method", "In the Consent Manager's portal, at any time, as easily as consent was given",
                "url", platform.withdrawalUrl()));
        processing.put("rights", List.of("access", "correction", "erasure", "withdrawal", "grievance", "nomination"));
        doc.put("processing", processing);

        Map<String, Object> parties = new LinkedHashMap<>();
        parties.put("dataSubject", header.get("subject"));
        parties.put("controller", controller);
        parties.put("consentManager", Map.of("name", platform.name(), "grievanceContact", platform.grievanceContact()));
        doc.put("parties", parties);

        List<Map<String, Object>> events = new ArrayList<>();
        for (CnsRecEvt e : rec.events()) {
            events.add(event(e));
        }
        doc.put("events", events);
        CnsRecEvt last = rec.events().isEmpty() ? null : rec.events().get(rec.events().size() - 1);
        doc.put("status", last == null ? null : last.type().dpvStatus());
        return doc;
    }

    /** A receipt: the record as it stood after event {@code seq}, for the principal and offered to the fiduciary. */
    public static Map<String, Object> receipt(String rcptId, Instant issuedAt, int seq, Map<String, Object> record,
                                              Platform platform) {
        Map<String, Object> receipt = new LinkedHashMap<>();
        receipt.put("@type", "cm:ConsentReceipt");
        receipt.put("receiptId", rcptId);
        receipt.put("issuedAt", issuedAt.toString());
        receipt.put("issuer", platform.name());
        receipt.put("forEvent", seq);
        receipt.put("record", record);
        return receipt;
    }

    /** An ISO 8601 duration in days, as 27560 states durations: {@code P365D}. */
    private static String days(int n) {
        return java.time.Period.ofDays(n).toString();
    }

    /** Whole days as {@code P30D}; anything else, such as a consent ended mid-day, to the second. */
    private static String iso(Duration d) {
        return d.toSeconds() % 86_400 == 0 ? days((int) d.toDays()) : d.toString();
    }

    private static Map<String, Object> dCat(PrpsDCat d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", d.code());
        m.put("@type", d.dpv() == null ? "dpv:PersonalData" : d.dpv());
        m.put("sensitivity", d.sensitivity().name());
        m.put("necessary", d.necessary());
        m.put("consequenceOfRefusal", d.consequence());
        return m;
    }

    private static Map<String, Object> event(CnsRecEvt e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("seq", e.seq());
        m.put("time", e.occurredAt().toString());
        m.put("type", e.type().name().toLowerCase(java.util.Locale.ROOT));
        m.put("status", e.type().dpvStatus());
        m.put("by", e.actor().name().toLowerCase(java.util.Locale.ROOT));
        if (e.dCats() != null) {
            m.put("personalData", e.dCats().stream().sorted().toList());
        }
        if (e.validFrom() != null && e.validUntil() != null) {
            m.put("validFrom", e.validFrom().toString());
            m.put("validUntil", e.validUntil().toString());
            m.put("duration", iso(Duration.between(e.validFrom(), e.validUntil())));
        }
        if (e.detail() != null) {
            m.put("change", e.detail());
        }
        return m;
    }
}
