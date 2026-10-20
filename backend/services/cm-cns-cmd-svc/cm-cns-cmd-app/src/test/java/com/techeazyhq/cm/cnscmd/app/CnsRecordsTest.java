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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.techeazyhq.cm.cnscmd.dom.CnsRcpt;
import com.techeazyhq.cm.cnscmd.dom.CnsRcptIssuedEvt;
import com.techeazyhq.cm.cnscmd.dom.CnsRec;
import com.techeazyhq.cm.cnscmd.dom.CnsRecEvt;
import com.techeazyhq.cm.cnscmd.dom.CnsRecRepoPort;
import com.techeazyhq.cm.cnscmd.dom.CnsRecording;
import com.techeazyhq.cm.cnscmd.dom.PrpsDef;
import com.techeazyhq.cm.cnscmd.dom.PrpsNtc;
import com.techeazyhq.cm.cnscmd.dom.PrpsVer;
import com.techeazyhq.cm.cnscmd.dom.PrpsVerRef;
import com.techeazyhq.cm.cnscmd.dom.RcptSignerPort;
import com.techeazyhq.cm.domkit.DomainEvt;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Records render as 27560 documents for their principal and controller only; each event gets a signed receipt. */
class CnsRecordsTest {

    private static final Instant T0 = Instant.parse("2026-10-20T00:00:00Z");
    private static final CnsRecDoc.Platform PLATFORM = new CnsRecDoc.Platform("Consent Manager", "IN",
            "grievance@cm.example", "https://cm.example/consents");

    private final InMemoryPrpsDefs purposes = new InMemoryPrpsDefs().orderUpdates();
    private final PrpsVer v1 = purposes.findByCode("df-1", "ORDER_UPDATES").map(PrpsDef::current).block().orElseThrow();
    private final PrpsNtc notice = v1.content().notice(null).orElseThrow();
    private final List<CnsRcpt> saved = new ArrayList<>();
    private final List<DomainEvt> outbox = new ArrayList<>();
    private final List<Map<String, Object>> signedPayloads = new ArrayList<>();

    private final CnsRec rec = new CnsRec("rec-1", "req-1", "df-1", "dp-1", "ORDER_UPDATES",
            new PrpsVerRef(v1.id(), 1, notice.lang(), notice.hash()), T0, List.of(
                    evt(1, CnsRecEvt.Type.REQUESTED, CnsRecEvt.Actor.FIDUCIARY, Set.of("EMAIL", "NAME"), null),
                    evt(2, CnsRecEvt.Type.GIVEN, CnsRecEvt.Actor.PRINCIPAL, Set.of("EMAIL"), T0.plusSeconds(86_400 * 30L)),
                    evt(3, CnsRecEvt.Type.WITHDRAWN, CnsRecEvt.Actor.PRINCIPAL, null, null)));

    private final CnsRecRepoPort repo = new CnsRecRepoPort() {
        @Override
        public Mono<Void> record(CnsRecording.Entry entry) {
            return Mono.empty();
        }

        @Override
        public Mono<CnsRec> findByCnsReqId(String cnsReqId) {
            return Mono.justOrEmpty(cnsReqId.equals(rec.cnsReqId()) ? rec : null);
        }

        @Override
        public Flux<CnsRec> findBySubject(String dpId) {
            return Flux.just(rec).filter(r -> dpId.equals(r.dpId()));
        }

        @Override
        public Flux<Pending> pendingReceipts(int limit) {
            return Flux.empty();
        }

        @Override
        public Mono<CnsRcpt> saveReceipt(CnsRcpt rcpt) {
            saved.add(rcpt);
            return Mono.just(rcpt);
        }

        @Override
        public Flux<CnsRcpt> receipts(String recId) {
            return Flux.fromIterable(saved);
        }
    };

    private final RcptSignerPort signer = new RcptSignerPort() {
        @Override
        public Signed sign(Map<String, Object> receipt) {
            signedPayloads.add(receipt);
            return new Signed("cns-rcpt-test", "header.payload.signature");
        }

        @Override
        public String publicJwksJson() {
            return "{\"keys\":[]}";
        }
    };

    private final CnsRecords records = new CnsRecords(repo, purposes, dfId -> Mono.just("Acme Retail"), signer,
            (evt, type, id) -> Mono.fromRunnable(() -> outbox.add(evt)), PLATFORM, Clock.fixed(T0, ZoneOffset.UTC));

    private static CnsRecEvt evt(int seq, CnsRecEvt.Type type, CnsRecEvt.Actor actor, Set<String> dCats, Instant until) {
        return new CnsRecEvt(seq, UUID.randomUUID(), T0.plusSeconds(seq), type, actor, dCats,
                until == null ? null : T0, until, null);
    }

    @Test
    @SuppressWarnings("unchecked")
    void theRecordIsA27560DocumentWithItsNoticeProcessingPartiesAndEvents() {
        Map<String, Object> doc = records.document(rec).block();

        assertEquals("dpv:ConsentRecord", doc.get("@type"));
        Map<String, Object> notice = (Map<String, Object>) doc.get("notice");
        assertEquals("sha256:" + this.notice.hash(), notice.get("hash"));
        assertEquals(this.notice.text(), notice.get("text"), "the notice shown, as it was published");
        Map<String, Object> processing = (Map<String, Object>) doc.get("processing");
        assertEquals("dpv:Consent", processing.get("legalBasis"));
        assertEquals("Acme Retail", ((Map<String, Object>) processing.get("controller")).get("name"));
        assertEquals(List.of("IN"), processing.get("jurisdictions"));
        assertEquals("P365D", processing.get("storageDuration"));
        List<Map<String, Object>> data = (List<Map<String, Object>>) processing.get("personalData");
        assertEquals("dpv-pd:EmailAddress", data.get(0).get("@type"));
        assertEquals("grievance@cm.example",
                ((Map<String, Object>) ((Map<String, Object>) doc.get("parties")).get("consentManager"))
                        .get("grievanceContact"));
        List<Map<String, Object>> events = (List<Map<String, Object>>) doc.get("events");
        assertEquals(List.of("requested", "given", "withdrawn"), events.stream().map(e -> e.get("type")).toList());
        assertEquals("P30D", events.get(1).get("duration"));
        assertEquals("dpv:ConsentWithdrawn", doc.get("status"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void aReceiptSignsTheRecordAsItStoodAfterItsEventAndItsHashGoesToTheLedger() {
        CnsRcpt rcpt = records.issue("req-1", 2).block();

        Map<String, Object> payload = signedPayloads.get(0);
        assertEquals("cm:ConsentReceipt", payload.get("@type"));
        assertEquals(2, payload.get("forEvent"));
        List<?> events = (List<?>) ((Map<String, Object>) payload.get("record")).get("events");
        assertEquals(2, events.size(), "nothing after the event it is for");
        assertEquals("cns-rcpt-test", rcpt.kid());
        assertEquals(CnsRecords.sha256("header.payload.signature"), rcpt.sha256());
        CnsRcptIssuedEvt evt = assertInstanceOf(CnsRcptIssuedEvt.class, outbox.get(0));
        assertEquals(rcpt.sha256(), evt.sha256());
        assertEquals("df-1", evt.dfId());
    }

    @Test
    void onlyThePrincipalAndTheControllerSeeARecord() {
        assertEquals("rec-1", records.recordFor("req-1", "dp-1", null).block().id());
        assertEquals("rec-1", records.recordFor("req-1", null, "df-1").block().id());
        assertEquals(404, assertThrows(DomRuleViolation.class,
                () -> records.recordFor("req-1", "dp-2", null).block()).httpStatus());
        assertEquals(404, assertThrows(DomRuleViolation.class,
                () -> records.recordFor("req-1", null, "df-2").block()).httpStatus());
    }

    @Test
    @SuppressWarnings("unchecked")
    void theExportHoldsEveryRecordOfThePrincipalWithItsReceipts() {
        records.issue("req-1", 1).block();

        Map<String, Object> export = records.export("dp-1").block();
        List<Map<String, Object>> consents = (List<Map<String, Object>>) export.get("consents");

        assertEquals(1, consents.size());
        assertEquals(1, ((List<?>) consents.get(0).get("receipts")).size());
        assertTrue(((List<?>) records.export("dp-2").block().get("consents")).isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void aRecordStillRendersWhenTheControllersNameIsUnavailable() {
        CnsRecords noNames = new CnsRecords(repo, purposes, dfId -> Mono.error(new IllegalStateException("down")),
                signer, (evt, type, id) -> Mono.empty(), PLATFORM, Clock.fixed(T0, ZoneOffset.UTC));
        Map<String, Object> controller = (Map<String, Object>) ((Map<String, Object>) noNames.document(rec).block()
                .get("processing")).get("controller");
        assertEquals("df-1", controller.get("name"));
    }
}
