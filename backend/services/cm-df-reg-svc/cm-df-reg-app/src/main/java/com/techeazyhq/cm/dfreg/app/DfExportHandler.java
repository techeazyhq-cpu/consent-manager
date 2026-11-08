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


package com.techeazyhq.cm.dfreg.app;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.techeazyhq.cm.audkit.AuditTrail;
import com.techeazyhq.cm.dfreg.dom.DataExportPort;
import com.techeazyhq.cm.dfreg.dom.Df;
import com.techeazyhq.cm.dfreg.dom.DfApiKeyRepoPort;
import com.techeazyhq.cm.dfreg.dom.DfMemberDirectoryPort;
import com.techeazyhq.cm.dfreg.dom.DfRepoPort;
import com.techeazyhq.cm.dfreg.dom.DfRole;
import com.techeazyhq.cm.dfreg.dom.DfSts;
import com.techeazyhq.cm.dfreg.dom.DfTermsRepoPort;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Mono;

/**
 * A fiduciary's own data in one document (ADR-064), for an Owner or Administrator to keep when the organisation leaves, or
 * whenever it likes: the organisation's profile and standing, its team, its API keys (never the keys or secrets), its
 * acceptances of the terms, its purposes with every version, its consent records with their signed receipts, and its audit
 * ledger entries. Each of the two big sections is capped (5,000) and says if it was cut. Taking an export is itself audited.
 * It is not offered once the organisation has closed: by then its team and keys are gone.
 */
public class DfExportHandler {

    private final DfRepoPort dfs;
    private final DfMemberDirectoryPort members;
    private final DfApiKeyRepoPort keys;
    private final DfTermsRepoPort terms;
    private final DataExportPort data;
    private final AuditTrail audit;
    private final Clock clock;

    public DfExportHandler(DfRepoPort dfs, DfMemberDirectoryPort members, DfApiKeyRepoPort keys, DfTermsRepoPort terms,
                           DataExportPort data, AuditTrail audit, Clock clock) {
        this.dfs = dfs;
        this.members = members;
        this.keys = keys;
        this.terms = terms;
        this.data = data;
        this.audit = audit;
        this.clock = clock;
    }

    public Mono<Map<String, Object>> export(DfTeamCaller caller) {
        if (caller.platformStaff() || caller.role() == null || caller.dfId() == null
                || (caller.role() != DfRole.OWNER && caller.role() != DfRole.ADMIN)) {
            return Mono.error(new DomRuleViolation(403, "not-permitted", "Owners and Administrators export the organisation's data"));
        }
        return dfs.findById(caller.dfId()).switchIfEmpty(Mono.error(new DomRuleViolation(404, "df-not-found", "No DF " + caller.dfId())))
                .flatMap(df -> {
                    if (df.sts() == DfSts.CLOSED || df.sts() == DfSts.PENDING) {
                        return Mono.<Map<String, Object>>error(new DomRuleViolation(409, "export-unavailable",
                                "The organisation's data can be exported while it is active, suspended or leaving, not in status " + df.sts()));
                    }
                    return assemble(df);
                })
                .flatMap(bundle -> audit.record("tenant.exported", caller.dfId(), caller.dfId(), AuditTrail.details(
                        "consents", String.valueOf(count(bundle, "consents")), "auditEntries", String.valueOf(count(bundle, "audit"))))
                        .thenReturn(bundle));
    }

    private Mono<Map<String, Object>> assemble(Df df) {
        Mono<List<Map<String, Object>>> team = members.list(df.id()).map(m -> {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("email", m.email());
            o.put("name", m.name());
            o.put("role", m.role().name());
            return o;
        }).collectList();
        Mono<List<Map<String, Object>>> apiKeys = keys.findByDfId(df.id()).map(k -> {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("id", k.id());
            o.put("name", k.label());
            o.put("keyPrefix", k.keyPrefix());
            o.put("signingRequired", k.signingRequired());
            o.put("createdAt", k.createdAt().toString());
            o.put("lastUsedAt", k.lastUsedAt() == null ? null : k.lastUsedAt().toString());
            o.put("revokedAt", k.revokedAt() == null ? null : k.revokedAt().toString());
            return o;
        }).collectList();
        Mono<List<Map<String, Object>>> acceptances = terms.findByDfId(df.id()).map(a -> {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("document", a.doc().name().toLowerCase(java.util.Locale.ROOT));
            o.put("version", a.version());
            o.put("acceptedBy", a.acceptedBy());
            o.put("acceptedAt", a.acceptedAt().toString());
            return o;
        }).collectList();
        return Mono.zip(team, apiKeys, acceptances, data.consentsAndPurposes(df.id()), data.auditEntries(df.id())).map(t -> {
            Map<String, Object> bundle = new LinkedHashMap<>();
            bundle.put("@type", "cm:FiduciaryExport");
            bundle.put("exportedAt", clock.instant().toString());
            bundle.put("fiduciary", profile(df));
            bundle.put("team", t.getT1());
            bundle.put("apiKeys", t.getT2());
            bundle.put("termsAcceptances", t.getT3());
            bundle.put("purposes", t.getT4().get("purposes"));
            bundle.put("consents", t.getT4().get("consents"));
            bundle.put("receiptKeys", t.getT4().get("receiptKeys"));
            bundle.put("audit", t.getT5());
            bundle.put("notes", List.of(
                    "API keys are listed by their prefix only: the keys and signing secrets are never kept in the clear.",
                    "A section with truncated=true was cut at its limit; ask the platform for the rest.",
                    "Principals are identified by pseudonymous id only. Ledger payloads erased by a principal's own erasure are null."));
            return bundle;
        });
    }

    private static Map<String, Object> profile(Df df) {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("id", df.id());
        o.put("name", df.name());
        o.put("standing", df.sts().name());
        o.put("registeredAt", df.registeredAt().toString());
        o.put("activatedAt", df.activatedAt() == null ? null : df.activatedAt().toString());
        o.put("leavesAt", df.leavesAt() == null ? null : df.leavesAt().toString());
        o.put("plan", df.subPlnName() == null ? "FREE" : df.subPlnName());
        o.put("contactEmail", df.contactEmail());
        o.put("legalName", df.prfl().legalName());
        o.put("contactPhone", df.prfl().contactPhone());
        o.put("website", df.prfl().website());
        o.put("registeredAddress", df.prfl().registeredAddress());
        o.put("industry", df.prfl().industry());
        o.put("cin", df.prfl().cin());
        o.put("gstin", df.prfl().gstin());
        o.put("dpoName", df.prfl().dpoName());
        o.put("dpoEmail", df.prfl().dpoEmail());
        return o;
    }

    @SuppressWarnings("unchecked")
    private static Object count(Map<String, Object> bundle, String section) {
        Object s = bundle.get(section);
        return s instanceof Map<?, ?> m ? ((Map<String, Object>) m).getOrDefault("count", 0) : 0;
    }
}
