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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import org.junit.jupiter.api.Test;

import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import com.techeazyhq.cm.cnscmd.dom.CnsArt;
import com.techeazyhq.cm.cnscmd.dom.CnsArtFactory;
import com.techeazyhq.cm.cnscmd.dom.CnsArtSts;
import com.techeazyhq.cm.cnscmd.dom.DCat;
import com.techeazyhq.cm.cnscmd.dom.DpRef;
import com.techeazyhq.cm.cnscmd.dom.Prps;
import com.techeazyhq.cm.cnscmd.dom.Scp;
import com.techeazyhq.cm.cnscmd.dom.StatusSlotPort;
import com.techeazyhq.cm.cnscmd.dom.ValPrd;
import com.techeazyhq.cm.seckit.CnsTknKeys;
import com.techeazyhq.cm.seckit.CnsTknSigner;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** A fiduciary's status list, and the place a consent token names in it (ADR-077). */
class StatusListsTest {

    private static final Instant NOW = Instant.parse("2026-11-08T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String BASE = "https://api.example.com";

    /** Slots handed out in order, and a set of ended consents, in memory. */
    private static final class Slots implements StatusSlotPort {
        final Map<String, Integer> byArt = new HashMap<>();
        final Set<Integer> ended = new java.util.HashSet<>();
        int next = 7;

        @Override
        public Mono<Integer> slotFor(String dfId, String cnsArtId) {
            return Mono.fromSupplier(() -> byArt.computeIfAbsent(cnsArtId, k -> next++));
        }

        @Override
        public Flux<Entry> entries(String dfId) {
            List<Entry> out = new ArrayList<>();
            byArt.values().forEach(i -> out.add(new Entry(i, ended.contains(i))));
            return Flux.fromIterable(out);
        }
    }

    private static CnsTknSigner signer() throws Exception {
        return new CnsTknSigner(CnsTknKeys.loadOrCreate(Files.createTempDirectory("keys").resolve("jwks.json"), true, CLOCK));
    }

    private static CnsArt art(String id) {
        ValPrd valPrd = new ValPrd(NOW, NOW.plus(Duration.ofDays(30)));
        return CnsArtFactory.rehydrate(id, "req-" + id, "df-1", new DpRef.LnkdDpRef("dp-1"), new Prps("ORDER_UPDATES"),
                new Scp(Set.of(new DCat("EMAIL"))), valPrd, CnsArtSts.ISSUED, NOW, null);
    }

    private static boolean bit(byte[] bits, int idx) {
        return (bits[idx / 8] & (1 << (idx % 8))) != 0;
    }

    private static byte[] decode(String lst) throws DataFormatException {
        Inflater inflater = new Inflater();
        inflater.setInput(Base64.getUrlDecoder().decode(lst));
        byte[] out = new byte[StatusSlotPort.LIST_SIZE / 8];
        int n = inflater.inflate(out);
        assertEquals(out.length, n, "the whole list inflates");
        inflater.end();
        return out;
    }

    @Test
    void bitsAreLeastSignificantFirstWithinEachByteAndOnlyEndedConsentsAreSet() {
        byte[] bits = StatusLists.bitString(List.of(
                new StatusSlotPort.Entry(0, true), new StatusSlotPort.Entry(9, true), new StatusSlotPort.Entry(10, false),
                new StatusSlotPort.Entry(StatusSlotPort.LIST_SIZE - 1, true)), StatusSlotPort.LIST_SIZE);

        assertEquals(StatusSlotPort.LIST_SIZE / 8, bits.length);
        assertEquals(0b00000001, bits[0] & 0xff, "index 0 is the lowest bit of the first byte");
        assertEquals(0b00000010, bits[1] & 0xff, "index 9 is bit 1 of the second byte");
        assertFalse(bit(bits, 10), "a consent still in force is not set");
        assertTrue(bit(bits, StatusSlotPort.LIST_SIZE - 1));
    }

    @Test
    void aTokenNamesItsPlaceAndTheListAnswersForIt() throws Exception {
        Slots slots = new Slots();
        CnsTknSigner signer = signer();
        CnsTknIssuer issuer = new CnsTknIssuer(signer, Duration.ofMinutes(15), CLOCK, slots, BASE + "//");
        StatusLists lists = new StatusLists(slots, signer, BASE, Duration.ofSeconds(60), Duration.ZERO, CLOCK);

        String live = issuer.issueFor(art("a")).block().token();
        String withdrawn = issuer.issueFor(art("b")).block().token();
        issuer.issueFor(art("a")).block();
        assertEquals(7, slots.byArt.get("a"), "the same consent keeps its index");

        Map<?, ?> status = (Map<?, ?>) ((Map<?, ?>) SignedJWT.parse(live).getJWTClaimsSet().getClaim("status")).get("status_list");
        assertEquals(7L, ((Number) status.get("idx")).longValue());
        assertEquals(BASE + "/api/cns-cmd/status-lists/df-1", status.get("uri"), "a trailing slash on the base address is not doubled");
        assertEquals(8L, ((Number) ((Map<?, ?>) ((Map<?, ?>) SignedJWT.parse(withdrawn).getJWTClaimsSet().getClaim("status"))
                .get("status_list")).get("idx")).longValue());

        slots.ended.add(8);
        SignedJWT list = SignedJWT.parse(lists.tokenFor("df-1").block());
        assertEquals("statuslist+jwt", list.getHeader().getType().getType());
        assertEquals(BASE + "/api/cns-cmd/status-lists/df-1", list.getJWTClaimsSet().getSubject());
        assertEquals(60L, ((Number) list.getJWTClaimsSet().getClaim("ttl")).longValue());
        assertEquals(NOW.plusSeconds(60), list.getJWTClaimsSet().getExpirationTime().toInstant());

        Map<?, ?> sl = (Map<?, ?>) list.getJWTClaimsSet().getClaim("status_list");
        assertEquals(1L, ((Number) sl.get("bits")).longValue());
        byte[] bits = decode((String) sl.get("lst"));
        assertFalse(bit(bits, 7), "the consent in force");
        assertTrue(bit(bits, 8), "the withdrawn one");
    }

    @Test
    void theListIsSignedWithTheTokenKeys() throws Exception {
        Path dir = Files.createTempDirectory("keys");
        CnsTknKeys keys = CnsTknKeys.loadOrCreate(dir.resolve("jwks.json"), true, CLOCK);
        CnsTknSigner signer = new CnsTknSigner(keys);
        StatusLists lists = new StatusLists(new Slots(), signer, BASE, Duration.ofSeconds(60), Duration.ZERO, CLOCK);

        SignedJWT list = SignedJWT.parse(lists.tokenFor("df-1").block());
        assertTrue(list.verify(new RSASSAVerifier(keys.publicKey(list.getHeader().getKeyID()).toRSAPublicKey())));
    }

    @Test
    void aListIsReusedForTheCacheTimeSoAnOpenEndpointCostsOneQueryNotOnePerRequest() throws Exception {
        Slots slots = new Slots();
        slots.byArt.put("some-consent", 3);
        int[] queries = {0};
        StatusSlotPort counting = new StatusSlotPort() {
            @Override
            public Mono<Integer> slotFor(String dfId, String cnsArtId) {
                return slots.slotFor(dfId, cnsArtId);
            }

            @Override
            public Flux<Entry> entries(String dfId) {
                queries[0]++;
                return slots.entries(dfId);
            }
        };
        StatusLists lists = new StatusLists(counting, signer(), BASE, Duration.ofSeconds(60), Duration.ofSeconds(10), CLOCK);

        String first = lists.tokenFor("df-1").block();
        assertEquals(first, lists.tokenFor("df-1").block());
        assertEquals(1, queries[0]);
        lists.tokenFor("df-2").block();
        assertEquals(2, queries[0], "another fiduciary has its own list");
    }

    @Test
    void aMadeUpFiduciaryIsAnsweredButNeverKeptSoTheOpenEndpointCannotFillMemory() throws Exception {
        int[] queries = {0};
        StatusSlotPort none = new StatusSlotPort() {
            @Override
            public Mono<Integer> slotFor(String dfId, String cnsArtId) {
                return Mono.just(0);
            }

            @Override
            public Flux<Entry> entries(String dfId) {
                queries[0]++;
                return Flux.empty();
            }
        };
        StatusLists lists = new StatusLists(none, signer(), BASE, Duration.ofSeconds(60), Duration.ofSeconds(10), CLOCK);

        assertTrue(!lists.tokenFor("made-up").block().isBlank(), "an empty list is still a valid answer");
        lists.tokenFor("made-up").block();
        assertEquals(2, queries[0], "and nothing was cached for it");
    }

    @Test
    void withoutAnAddressTokensCarryNoStatusAndStillIssue() throws Exception {
        CnsTknIssuer issuer = new CnsTknIssuer(signer(), Duration.ofMinutes(15), CLOCK, new Slots(), "");

        String token = issuer.issueFor(art("a")).block().token();
        assertEquals(null, SignedJWT.parse(token).getJWTClaimsSet().getClaim("status"));
    }
}
