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

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.techeazyhq.cm.cnscmd.dom.CnsRcpt;
import com.techeazyhq.cm.cnscmd.dom.CnsRcptIssuedEvt;
import com.techeazyhq.cm.cnscmd.dom.CnsRec;
import com.techeazyhq.cm.cnscmd.dom.CnsRecRepoPort;
import com.techeazyhq.cm.cnscmd.dom.DfNamePort;
import com.techeazyhq.cm.cnscmd.dom.ObxPort;
import com.techeazyhq.cm.cnscmd.dom.PrpsDefRepoPort;
import com.techeazyhq.cm.cnscmd.dom.PrpsVer;
import com.techeazyhq.cm.cnscmd.dom.RcptSignerPort;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Consent records and receipts (ADR-025): rendering a record as its 27560 document, issuing a signed receipt for
 * every record event, and serving both to the principal the record names and the fiduciary that is its controller.
 * Anyone else is told it doesn't exist.
 */
public class CnsRecords {

    private final CnsRecRepoPort recs;
    private final PrpsDefRepoPort prpsDefs;
    private final DfNamePort dfNames;
    private final RcptSignerPort signer;
    private final ObxPort outbox;
    private final CnsRecDoc.Platform platform;
    private final Clock clock;

    public CnsRecords(CnsRecRepoPort recs, PrpsDefRepoPort prpsDefs, DfNamePort dfNames, RcptSignerPort signer,
                      ObxPort outbox, CnsRecDoc.Platform platform, Clock clock) {
        this.recs = recs;
        this.prpsDefs = prpsDefs;
        this.dfNames = dfNames;
        this.signer = signer;
        this.outbox = outbox;
        this.platform = platform;
        this.clock = clock;
    }

    /** A record a viewer may see: as the principal it names ({@code dpId}) or as its controller ({@code dfId}). */
    public Mono<CnsRec> recordFor(String cnsReqId, String dpId, String dfId) {
        return recs.findByCnsReqId(cnsReqId)
                .filter(rec -> (dpId != null && dpId.equals(rec.dpId())) || (dfId != null && dfId.equals(rec.dfId())))
                .switchIfEmpty(Mono.error(() -> new DomRuleViolation(404, "record-not-found", "No record for " + cnsReqId)));
    }

    /** The record as its 27560 JSON-LD document. */
    public Mono<Map<String, Object>> document(CnsRec rec) {
        Mono<PrpsVer> ver = rec.prpsVer() == null ? Mono.empty() : prpsDefs.findVersion(rec.prpsVer().prpsVerId());
        return Mono.zip(ver.map(java.util.Optional::of).defaultIfEmpty(java.util.Optional.empty()),
                        dfNames.nameOf(rec.dfId()).map(java.util.Optional::of).onErrorResume(e -> Mono.empty())
                                .defaultIfEmpty(java.util.Optional.empty()))
                .map(t -> CnsRecDoc.render(rec, t.getT1().orElse(null), t.getT2().orElse(null), platform));
    }

    public Flux<CnsRcpt> receipts(CnsRec rec) {
        return recs.receipts(rec.id());
    }

    /**
     * Everything the platform holds about a principal's consents, machine-readable (DPDPA First Schedule B4, GDPR
     * art. 20): every record as its 27560 document, with every receipt as signed.
     */
    public Mono<Map<String, Object>> export(String dpId) {
        return recs.findBySubject(dpId)
                .concatMap(rec -> Mono.zip(document(rec), receipts(rec).map(CnsRecords::receiptView).collectList())
                        .map(t -> {
                            Map<String, Object> entry = new LinkedHashMap<>();
                            entry.put("record", t.getT1());
                            entry.put("receipts", t.getT2());
                            return entry;
                        }))
                .collectList()
                .map(entries -> {
                    Map<String, Object> export = new LinkedHashMap<>();
                    export.put("@type", "cm:ConsentExport");
                    export.put("exportedAt", clock.instant().toString());
                    export.put("subject", "urn:cm:dp:" + dpId);
                    export.put("receiptKeys", "/api/cns-cmd/.well-known/receipt-jwks.json");
                    export.put("consents", entries);
                    return export;
                });
    }

    public static Map<String, Object> receiptView(CnsRcpt r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("receiptId", r.id());
        m.put("forEvent", r.seq());
        m.put("issuedAt", r.issuedAt().toString());
        m.put("kid", r.kid());
        m.put("sha256", r.sha256());
        m.put("jws", r.jws());
        return m;
    }

    /**
     * Issues the receipt for record event {@code seq} (ADR-025): the record as it stood after that event, signed with
     * the receipt key. Its hash goes to the audit ledger in the same transaction, so the ledger's anchored checkpoints
     * give it trusted time.
     */
    @Transactional
    public Mono<CnsRcpt> issue(String cnsReqId, int seq) {
        return recs.findByCnsReqId(cnsReqId).flatMap(rec -> document(rec.upTo(seq)).flatMap(doc -> {
            String rcptId = UUID.randomUUID().toString();
            Instant issuedAt = clock.instant();
            RcptSignerPort.Signed signed = signer.sign(CnsRecDoc.receipt(rcptId, issuedAt, seq, doc, platform));
            CnsRcpt rcpt = new CnsRcpt(rcptId, rec.id(), rec.cnsReqId(), seq, issuedAt, signed.kid(), signed.jws(),
                    sha256(signed.jws()));
            return recs.saveReceipt(rcpt)
                    .flatMap(saved -> outbox.enqueue(CnsRcptIssuedEvt.of(saved, rec.dfId()), "CnsRec", rec.id())
                            .thenReturn(saved));
        }));
    }

    static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
