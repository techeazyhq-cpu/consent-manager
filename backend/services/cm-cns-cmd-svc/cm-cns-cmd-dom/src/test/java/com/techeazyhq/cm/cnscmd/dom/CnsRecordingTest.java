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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** Every change of a consent writes its 27560 record; nothing else does (ADR-025). */
class CnsRecordingTest {

    private static final PrpsVerRef V1 = new PrpsVerRef("ver-1", 1, "en", "hash");
    private static final Scp BOTH = new Scp(Set.of(new DCat("EMAIL"), new DCat("NAME")));

    private static CnsArt given() {
        return CnsArtFactory.rehydrate("art-1", "req-1", "df-1", DpRef.linked("dp-1"), new Prps("P"), BOTH,
                ValPrd.of(Instant.now(), Duration.ofDays(30)), CnsArtSts.ISSUED, Instant.now(), null);
    }

    @Test
    void aRequestOpensItsRecordWithoutTheContactItWasSentTo() {
        CnsReq req = CnsReqFactory.create("df-1", DpRef.unlinked(ExtDpRefTyp.EMAIL, "asha@example.com"),
                CnsReqTemplate.of(new Prps("P"), BOTH, "T", "X", V1));

        CnsRecording.Open open = assertInstanceOf(CnsRecording.Open.class,
                CnsRecording.of(req.pullEvts().get(0)).orElseThrow());

        assertEquals(req.id(), open.header().cnsReqId());
        assertNull(open.header().dpId(), "no subject until linked, and never the contact");
        assertEquals(V1, open.header().prpsVer());
        assertEquals(CnsRecEvt.Type.REQUESTED, open.first().type());
        assertEquals(CnsRecEvt.Actor.FIDUCIARY, open.first().actor());
        assertEquals(Set.of("EMAIL", "NAME"), open.first().dCats());
    }

    @Test
    void eachChangeOfAConsentAppendsItsEventWithItsDpvStatus() {
        CnsArt art = given();
        art.withdrawCategory(new DCat("NAME"), Instant.now());
        art.renew(60, 90, Instant.now());
        art.revoke();

        List<CnsRecEvt> recorded = art.pullEvts().stream().map(CnsRecording::of)
                .map(e -> assertInstanceOf(CnsRecording.Append.class, e.orElseThrow()))
                .peek(a -> assertEquals("req-1", a.cnsReqId()))
                .map(CnsRecording.Append::evt)
                .toList();

        assertEquals(List.of(CnsRecEvt.Type.MODIFIED, CnsRecEvt.Type.RENEWED, CnsRecEvt.Type.WITHDRAWN),
                recorded.stream().map(CnsRecEvt::type).toList());
        assertEquals("CATEGORY_WITHDRAWN:NAME", recorded.get(0).detail());
        assertEquals(Set.of("EMAIL"), recorded.get(0).dCats());
        assertEquals("dpv:RenewedConsentGiven", recorded.get(1).type().dpvStatus());
        assertEquals("dpv:ConsentWithdrawn", recorded.get(2).type().dpvStatus());
    }

    @Test
    void aLapseIsTheRecordedEventOfThePlatform() {
        CnsArt art = given();
        art.expire(art.valPrd().until().plusSeconds(1));

        CnsRecEvt evt = assertInstanceOf(CnsRecording.Append.class,
                CnsRecording.of(art.pullEvts().get(0)).orElseThrow()).evt();

        assertEquals(CnsRecEvt.Type.LAPSED, evt.type());
        assertEquals(CnsRecEvt.Actor.PLATFORM, evt.actor());
        assertEquals("dpv:ConsentExpired", evt.type().dpvStatus());
    }

    @Test
    void linkingARequestGivesItsRecordASubjectAndOtherEventsWriteNothing() {
        assertEquals(new CnsRecording.Subject("req-1", "dp-9"),
                CnsRecording.of(CnsDpLinkedEvt.of("req-1", "CnsReq", "dp-9")).orElseThrow());
        assertTrue(CnsRecording.of(CnsDpLinkedEvt.of("art-1", "CnsArt", "dp-9")).isEmpty());

        CnsRecording.Append refusal = assertInstanceOf(CnsRecording.Append.class,
                CnsRecording.of(CnsDeniedEvt.of("req-1", "df-1", DpRef.linked("dp-1"))).orElseThrow());
        assertEquals(CnsRecEvt.Type.REFUSED, refusal.evt().type());

        PrpsDef purpose = PrpsDef.create("df-1", "P", new PrpsVerContent("P", null,
                List.of(new PrpsDCat("EMAIL", null, null, false, null)), null, null, 30, 30, 30, null,
                List.of(new PrpsNtc("en", "T", "X"))), Instant.now());
        purpose.publish(Instant.now());
        assertTrue(CnsRecording.of(purpose.pullEvts().get(0)).isEmpty(), "publishing a purpose isn't a consent's event");
    }

    @Test
    void aReceiptShowsTheRecordAsItStoodAfterItsEvent() {
        CnsRecEvt e1 = new CnsRecEvt(1, java.util.UUID.randomUUID(), Instant.now(), CnsRecEvt.Type.REQUESTED,
                CnsRecEvt.Actor.FIDUCIARY, null, null, null, null);
        CnsRecEvt e2 = e1.at(2);
        CnsRec rec = new CnsRec("rec-1", "req-1", "df-1", "dp-1", "P", V1, Instant.now(), List.of(e1, e2));

        assertEquals(List.of(e1), rec.upTo(1).events());
        assertEquals(2, rec.upTo(2).events().size());
    }
}
