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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.techeazyhq.cm.cnscmd.dom.CnsNotificationFailedEvt;
import com.techeazyhq.cm.cnscmd.dom.CnsReq;
import com.techeazyhq.cm.cnscmd.dom.CnsReqFactory;
import com.techeazyhq.cm.cnscmd.dom.CnsReqRepoPort;
import com.techeazyhq.cm.cnscmd.dom.CnsReqSts;
import com.techeazyhq.cm.cnscmd.dom.CnsReqTemplate;
import com.techeazyhq.cm.cnscmd.dom.DCat;
import com.techeazyhq.cm.cnscmd.dom.DpRef;
import com.techeazyhq.cm.cnscmd.dom.ExtDpRefTyp;
import com.techeazyhq.cm.cnscmd.dom.NtfCmdPort;
import com.techeazyhq.cm.cnscmd.dom.Prps;
import com.techeazyhq.cm.cnscmd.dom.PrpsVerRef;
import com.techeazyhq.cm.cnscmd.dom.Scp;
import com.techeazyhq.cm.domkit.DomainEvt;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class CnsLifecycleSagaOrchestratorTest {

    private final Map<String, CnsReq> reqs = new HashMap<>();
    private final List<DomainEvt> published = new ArrayList<>();
    private final CnsLifecycleSagaOrchestrator saga = new CnsLifecycleSagaOrchestrator(new InMemoryReqs(), new NtfCmdPort() {
        @Override
        public Mono<Void> notifyDp(CnsReq cnsReq) {
            return Mono.empty();
        }

        @Override
        public Mono<Void> notifyDf(String dfId, String subjectId, String outcome) {
            return Mono.empty();
        }
    });

    @Test
    void aPendingRequestWhosePrincipalCouldNotBeReachedIsMarkedFailedAndSaysSo() {
        CnsReq req = stored(CnsReqSts.PENDING);

        saga.onDpDeliveryFailed(req.id()).block();

        assertEquals(CnsReqSts.NOTIFICATION_FAILED, reqs.get(req.id()).sts());
        assertEquals(1, published.size());
        CnsNotificationFailedEvt evt = assertInstanceOf(CnsNotificationFailedEvt.class, published.get(0));
        assertEquals(req.id(), evt.cnsReqId());
        assertEquals("df-1", evt.dfId());
    }

    @Test
    void aRequestThePrincipalAlreadyDecidedKeepsItsDecision() {
        CnsReq req = stored(CnsReqSts.GRANTED);

        saga.onDpDeliveryFailed(req.id()).block();

        assertEquals(CnsReqSts.GRANTED, reqs.get(req.id()).sts());
        assertTrue(published.isEmpty());
    }

    @Test
    void anUnknownRequestIsIgnored() {
        saga.onDpDeliveryFailed("no-such-request").block();

        assertTrue(published.isEmpty());
    }

    private CnsReq stored(CnsReqSts sts) {
        CnsReq req = CnsReqFactory.rehydrate("req-" + sts, "df-1",
                new DpRef.UnlnkDpRef(ExtDpRefTyp.EMAIL, "ann@example.com"), new CnsReqTemplate(new Prps("ORDER_FULFILLMENT"),
                        new Scp(Set.of(new DCat("EMAIL"))), "Title", "Text", new PrpsVerRef("ver-1", 1, "en", "hash")),
                sts, Instant.now());
        reqs.put(req.id(), req);
        return req;
    }

    private final class InMemoryReqs implements CnsReqRepoPort {
        @Override
        public Mono<CnsReq> save(CnsReq cnsReq) {
            reqs.put(cnsReq.id(), cnsReq);
            published.addAll(cnsReq.pullEvts());
            return Mono.just(cnsReq);
        }

        @Override
        public Mono<CnsReq> findById(String id) {
            return Mono.justOrEmpty(reqs.get(id));
        }

        @Override
        public Flux<CnsReq> findUnlinkedByExtRef(ExtDpRefTyp typ, String val) {
            return Flux.empty();
        }
    }
}
