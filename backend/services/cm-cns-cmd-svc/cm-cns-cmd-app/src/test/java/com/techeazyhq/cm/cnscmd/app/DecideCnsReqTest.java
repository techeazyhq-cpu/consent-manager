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

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.techeazyhq.cm.cnscmd.dom.CnsArt;
import com.techeazyhq.cm.cnscmd.dom.CnsArtRepoPort;
import com.techeazyhq.cm.cnscmd.dom.CnsArtSts;
import com.techeazyhq.cm.cnscmd.dom.CnsGrantedEvt;
import com.techeazyhq.cm.cnscmd.dom.CnsReq;
import com.techeazyhq.cm.cnscmd.dom.CnsReqRepoPort;
import com.techeazyhq.cm.cnscmd.dom.CnsReqSts;
import com.techeazyhq.cm.cnscmd.dom.DCat;
import com.techeazyhq.cm.cnscmd.dom.DpRef;
import com.techeazyhq.cm.cnscmd.dom.ExtDpRefTyp;
import com.techeazyhq.cm.cnscmd.dom.CnsReqFactory;
import com.techeazyhq.cm.domkit.DomainEvt;
import com.techeazyhq.cm.seckit.CnsTknKeys;
import com.techeazyhq.cm.seckit.CnsTknSigner;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Only the principal a request names decides on it, and on what terms (ADR-026). A fiduciary approving its own
 * request was accepted until this was checked.
 */
class DecideCnsReqTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-20T00:00:00Z"), ZoneOffset.UTC);

    private final InMemoryPrpsDefs purposes = new InMemoryPrpsDefs().orderUpdates();
    private final Map<String, CnsReq> reqs = new HashMap<>();
    private final Map<String, CnsArt> arts = new HashMap<>();
    private final List<DomainEvt> outbox = new ArrayList<>();

    private final CnsReqRepoPort reqRepo = new CnsReqRepoPort() {
        @Override
        public Mono<CnsReq> save(CnsReq cnsReq) {
            reqs.put(cnsReq.id(), cnsReq);
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
    };

    private final CnsArtRepoPort artRepo = new CnsArtRepoPort() {
        @Override
        public Mono<CnsArt> save(CnsArt cnsArt) {
            arts.put(cnsArt.id(), cnsArt);
            return Mono.just(cnsArt);
        }

        @Override
        public Mono<CnsArt> findById(String id) {
            return Mono.justOrEmpty(arts.get(id));
        }

        @Override
        public Flux<CnsArt> findUnlinkedByExtRef(ExtDpRefTyp typ, String val) {
            return Flux.empty();
        }

        @Override
        public Flux<CnsArt> findIssuedExpiredAsOf(Instant now) {
            return Flux.empty();
        }
    };

    private final CnsReqTerms terms = new CnsReqTerms(reqRepo, purposes);
    private final ApproveCnsCmdHandler approve = new ApproveCnsCmdHandler(reqRepo, artRepo,
            (evt, type, id) -> Mono.fromRunnable(() -> outbox.add(evt)),
            new CnsTknIssuer(signer(), Duration.ofMinutes(15), CLOCK), terms);
    private final DenyCnsReqCmdHandler deny = new DenyCnsReqCmdHandler(reqRepo);
    private final RevokeCnsArtCmdHandler revoke = new RevokeCnsArtCmdHandler(artRepo);

    /** ORDER_UPDATES version 1 asks EMAIL (needed) and NAME for 180 days, up to 365, of principal dp-1. */
    private CnsReq pending() {
        CnsReq req = CnsReqFactory.create("df-1", DpRef.linked("dp-1"),
                purposes.findByCode("df-1", "ORDER_UPDATES").block().templateFor(1, null, null));
        reqs.put(req.id(), req);
        return req;
    }

    @Test
    void thePrincipalGivesSomeOfTheDataForAShorterTime() {
        CnsReq req = pending();

        CnsArt art = approve.handle(new ApproveCnsReqCmd(req.id(), "dp-1", Set.of("EMAIL"), 30)).block();

        assertEquals(Set.of(new DCat("EMAIL")), art.scp().dCats());
        assertEquals(Duration.ofDays(30), Duration.between(art.valPrd().from(), art.valPrd().until()));
        assertEquals(List.of("EMAIL"), scopeOf(art.cnsTkn()), "the token says what the consent covers");
        CnsGrantedEvt evt = assertInstanceOf(CnsGrantedEvt.class, outbox.get(0));
        assertEquals(Set.of(new DCat("EMAIL")), evt.scp().dCats());
        assertEquals(30, evt.validityDays());
    }

    @Test
    void withNoChoiceTheConsentCoversEverythingAskedForTheRequestedValidity() {
        CnsReq req = pending();

        CnsArt art = approve.handle(new ApproveCnsReqCmd(req.id(), "dp-1", null, null)).block();

        assertEquals(req.scp(), art.scp());
        assertEquals(Duration.ofDays(180), Duration.between(art.valPrd().from(), art.valPrd().until()));
    }

    @Test
    void aValidityPastTheMaximumOrDataNotAskedIsRefused() {
        CnsReq req = pending();

        assertEquals(400, refusal(() -> approve.handle(new ApproveCnsReqCmd(req.id(), "dp-1", null, 366)).block()));
        assertEquals(400, refusal(() -> approve.handle(
                new ApproveCnsReqCmd(req.id(), "dp-1", Set.of("LOCATION"), null)).block()));
        assertEquals(CnsReqSts.PENDING, reqs.get(req.id()).sts(), "nothing was decided");
    }

    @Test
    void nobodyButThePrincipalCanApproveOrDecline() {
        CnsReq req = pending();

        // a fiduciary's caller has no dpId; another principal has a different one: both are told it doesn't exist
        assertEquals(404, refusal(() -> approve.handle(new ApproveCnsReqCmd(req.id(), null, null, null)).block()));
        assertEquals(404, refusal(() -> approve.handle(new ApproveCnsReqCmd(req.id(), "dp-2", null, null)).block()));
        assertEquals(404, refusal(() -> deny.handle(new DenyCnsReqCmd(req.id(), "dp-2")).block()));
        assertEquals(CnsReqSts.PENDING, reqs.get(req.id()).sts());
        assertTrue(arts.isEmpty());

        assertEquals(CnsReqSts.DENIED, deny.handle(new DenyCnsReqCmd(req.id(), "dp-1")).block().sts());
    }

    @Test
    void nobodyButThePrincipalCanWithdraw() {
        CnsArt art = approve.handle(new ApproveCnsReqCmd(pending().id(), "dp-1", null, null)).block();

        assertEquals(404, refusal(() -> revoke.handle(new RevokeCnsArtCmd(art.id(), "dp-2")).block()));
        assertEquals(404, refusal(() -> revoke.handle(new RevokeCnsArtCmd(art.id(), null)).block()));
        assertEquals(CnsArtSts.ISSUED, arts.get(art.id()).sts());

        assertEquals(CnsArtSts.REVOKED, revoke.handle(new RevokeCnsArtCmd(art.id(), "dp-1")).block().sts());
    }

    @Test
    void theTermsAreForThePrincipalAndTheFiduciaryOnly() {
        CnsReq req = pending();

        assertEquals(365, terms.forViewer(req.id(), "dp-1", null).block().version().maxValidityDays());
        assertEquals(req.id(), terms.forViewer(req.id(), null, "df-1").block().req().id());
        assertEquals(404, refusal(() -> terms.forViewer(req.id(), "dp-2", null).block()));
        assertEquals(404, refusal(() -> terms.forViewer(req.id(), null, "df-2").block()));
    }

    private static List<String> scopeOf(String token) {
        try {
            return com.nimbusds.jwt.SignedJWT.parse(token).getJWTClaimsSet().getStringListClaim("scope");
        } catch (java.text.ParseException e) {
            throw new IllegalStateException(e);
        }
    }

    private static int refusal(Runnable call) {
        return assertThrows(DomRuleViolation.class, call::run).httpStatus();
    }

    private static CnsTknSigner signer() {
        try {
            Path keys = Files.createTempDirectory("cns-tkn-keys").resolve("jwks.json");
            return new CnsTknSigner(CnsTknKeys.loadOrCreate(keys, true, CLOCK));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
