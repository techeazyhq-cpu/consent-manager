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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.nimbusds.jwt.SignedJWT;
import com.techeazyhq.cm.cnscmd.dom.CnsArt;
import com.techeazyhq.cm.cnscmd.dom.CnsArtFactory;
import com.techeazyhq.cm.cnscmd.dom.CnsArtRepoPort;
import com.techeazyhq.cm.cnscmd.dom.CnsArtSts;
import com.techeazyhq.cm.cnscmd.dom.DCat;
import com.techeazyhq.cm.cnscmd.dom.DpRef;
import com.techeazyhq.cm.cnscmd.dom.ExtDpRefTyp;
import com.techeazyhq.cm.cnscmd.dom.Prps;
import com.techeazyhq.cm.cnscmd.dom.Scp;
import com.techeazyhq.cm.cnscmd.dom.ValPrd;
import com.techeazyhq.cm.seckit.CnsTknKeys;
import com.techeazyhq.cm.seckit.CnsTknSigner;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** ADR-027: tokens outlive neither their lifetime nor the consent; status comes from the write side. */
class ShortLivedTokenTest {

    private static final Instant NOW = Instant.parse("2026-10-11T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final Map<String, CnsArt> arts = new HashMap<>();
    private final CnsTknIssuer issuer = new CnsTknIssuer(signer(), Duration.ofMinutes(15), CLOCK);

    @Test
    void aTokenLivesForItsLifetimeNotForTheWholeConsent() throws Exception {
        CnsArt art = art("art-1", "df-1", ValPrd.of(NOW.minusSeconds(60), Duration.ofDays(365)));

        CnsTknIssuer.Issued issued = issuer.issue(art);

        assertEquals(NOW.plus(Duration.ofMinutes(15)), issued.exp());
        assertEquals(issued.exp(), SignedJWT.parse(issued.token()).getJWTClaimsSet().getExpirationTime().toInstant());
    }

    @Test
    void aTokenNeverOutlivesTheConsent() {
        CnsArt art = art("art-1", "df-1", ValPrd.of(NOW.minusSeconds(60), Duration.ofMinutes(5)));

        assertEquals(NOW.plus(Duration.ofMinutes(4)), issuer.issue(art).exp());
    }

    @Test
    void anIssuedArtefactPastItsValidityIsAlreadyExpiredBeforeTheSweepRuns() {
        CnsArt lapsed = art("art-1", "df-1", ValPrd.of(NOW.minus(Duration.ofDays(2)), Duration.ofDays(1)));

        assertEquals(CnsArtSts.ISSUED, lapsed.sts());
        assertEquals(CnsArtSts.EXPIRED, lapsed.liveStatus(NOW));
    }

    @Test
    void theOwningFiduciaryRefreshesAValidConsent() {
        store(art("art-1", "df-1", ValPrd.of(NOW.minusSeconds(60), Duration.ofDays(30))));

        CnsTknIssuer.Issued issued = refresh().handle(new RefreshCnsTknCmdHandler.Cmd("art-1", "df-1")).block();

        assertEquals(NOW.plus(Duration.ofMinutes(15)), issued.exp());
    }

    @Test
    void anotherFiduciaryLearnsNothingAboutTheArtefact() {
        store(art("art-1", "df-1", ValPrd.of(NOW.minusSeconds(60), Duration.ofDays(30))));

        DomRuleViolation notTheirs = refusal(new RefreshCnsTknCmdHandler.Cmd("art-1", "df-2"));
        DomRuleViolation missing = refusal(new RefreshCnsTknCmdHandler.Cmd("no-such-art", "df-2"));

        assertEquals(404, notTheirs.httpStatus());
        assertEquals(missing.problemType(), notTheirs.problemType());
    }

    @Test
    void aRevokedOrLapsedConsentGetsNoToken() {
        CnsArt revoked = art("art-r", "df-1", ValPrd.of(NOW.minusSeconds(60), Duration.ofDays(30)));
        revoked.revoke();
        store(revoked);
        store(art("art-l", "df-1", ValPrd.of(NOW.minus(Duration.ofDays(2)), Duration.ofDays(1))));

        assertEquals("cns-art-not-active", refusal(new RefreshCnsTknCmdHandler.Cmd("art-r", "df-1")).problemType());
        assertEquals("cns-art-not-active", refusal(new RefreshCnsTknCmdHandler.Cmd("art-l", "df-1")).problemType());
    }

    @Test
    void theLiveStatusIsTheWriteSidesAtTheMomentOfAsking() {
        CnsArt revoked = art("art-r", "df-1", ValPrd.of(NOW.minusSeconds(60), Duration.ofDays(30)));
        revoked.revoke();
        store(revoked);
        GetCnsArtLiveStatusQryHandler status = new GetCnsArtLiveStatusQryHandler(new InMemoryArts(), CLOCK);

        assertEquals(CnsArtSts.REVOKED, status.handle("art-r").block());
        assertEquals(null, status.handle("no-such-art").block());
    }

    private RefreshCnsTknCmdHandler refresh() {
        return new RefreshCnsTknCmdHandler(new InMemoryArts(), issuer, CLOCK);
    }

    private DomRuleViolation refusal(RefreshCnsTknCmdHandler.Cmd cmd) {
        return assertThrows(DomRuleViolation.class, () -> refresh().handle(cmd).block());
    }

    private void store(CnsArt art) {
        art.pullEvts();
        arts.put(art.id(), art);
    }

    private static CnsArt art(String id, String dfId, ValPrd valPrd) {
        return CnsArtFactory.rehydrate(id, "req-" + id, dfId, new DpRef.LnkdDpRef("dp-1"), new Prps("ORDER_FULFILLMENT"),
                new Scp(Set.of(new DCat("EMAIL"))), valPrd, CnsArtSts.ISSUED, valPrd.from(), null);
    }

    private static CnsTknSigner signer() {
        try {
            Path keys = Files.createTempDirectory("cns-tkn-keys").resolve("jwks.json");
            return new CnsTknSigner(CnsTknKeys.loadOrCreate(keys, true, CLOCK));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private final class InMemoryArts implements CnsArtRepoPort {
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
    }
}
