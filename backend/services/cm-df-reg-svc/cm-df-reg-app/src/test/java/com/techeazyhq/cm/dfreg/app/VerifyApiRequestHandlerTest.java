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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.techeazyhq.cm.dfreg.dom.Df;
import com.techeazyhq.cm.dfreg.dom.DfApiKey;
import com.techeazyhq.cm.dfreg.dom.DfApiKeyRepoPort;
import com.techeazyhq.cm.dfreg.dom.DfFactory;
import com.techeazyhq.cm.dfreg.dom.DfPrfl;
import com.techeazyhq.cm.dfreg.dom.DfRepoPort;
import com.techeazyhq.cm.dfreg.dom.DfSts;
import com.techeazyhq.cm.dfreg.dom.RequestNoncePort;
import com.techeazyhq.cm.dfreg.dom.RequestSignature;
import com.techeazyhq.cm.dfreg.dom.SecretSealerPort;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** ADR-041: a key, its signature, its time window and its nonce, checked in that order. */
class VerifyApiRequestHandlerTest {

    private static final Instant NOW = Instant.parse("2026-10-19T10:00:00Z");
    private static final String BODY = "{\"prpsCode\":\"ORDER_UPDATES\"}";
    private static final String BODY_SHA = RequestSignature.sha256Hex(BODY.getBytes(StandardCharsets.UTF_8));

    private final Map<String, DfApiKey> byHash = new HashMap<>();
    private final Set<String> claimed = new HashSet<>();
    private final Map<String, Df> dfs = new HashMap<>();

    private final SecretSealerPort sealer = new SecretSealerPort() {
        public String seal(String s) {
            return "sealed:" + s;
        }

        public String open(String s) {
            return s.substring(7);
        }
    };

    private final DfApiKeyRepoPort keys = new DfApiKeyRepoPort() {
        public Mono<DfApiKey> save(DfApiKey k) {
            byHash.put(k.keyHash(), k);
            return Mono.just(k);
        }

        public Mono<DfApiKey> findById(String id) {
            return Flux.fromIterable(byHash.values()).filter(k -> k.id().equals(id)).next();
        }

        public Mono<DfApiKey> findByHash(String h) {
            return Mono.justOrEmpty(byHash.get(h));
        }

        public Flux<DfApiKey> findByDfId(String dfId) {
            return Flux.fromIterable(byHash.values());
        }

        public Mono<Void> touch(String id, Instant at) {
            return Mono.empty();
        }
    };

    private final RequestNoncePort nonces = new RequestNoncePort() {
        public Mono<Boolean> claim(String keyId, String nonce, Instant expiresAt) {
            return Mono.just(claimed.add(keyId + "/" + nonce));
        }

        public Mono<Long> purgeExpired(Instant now) {
            return Mono.just(0L);
        }
    };

    private final DfRepoPort dfRepo = new DfRepoPort() {
        public Mono<Df> save(Df df) {
            return Mono.just(df);
        }

        public Mono<Df> findById(String id) {
            return Mono.justOrEmpty(dfs.get(id));
        }

        public Flux<Df> findAll() {
            return Flux.fromIterable(dfs.values());
        }
    };

    private final VerifyApiRequestHandler handler = new VerifyApiRequestHandler(keys, dfRepo, nonces, sealer,
            (dfId, secret) -> Mono.just("token-for-" + dfId), Clock.fixed(NOW, ZoneOffset.UTC));

    private DfApiKey.Issued issued;

    VerifyApiRequestHandlerTest() {
        dfs.put("df-1", DfFactory.rehydrate("df-1", "Acme", "ops@acme.example", DfPrfl.EMPTY, null, DfSts.ACTIVE,
                NOW, "client-secret", NOW));
        issued = DfApiKey.issue("df-1", "Orders backend", false, "u-1", sealer);
        keys.save(issued.key()).block();
    }

    private VerifyApiRequestHandler.Request signed(String method, long at, String nonce) {
        String ts = String.valueOf(at);
        String canonical = RequestSignature.canonical(ts, nonce, method, "/api/cns-cmd/cns-reqs", null, BODY_SHA);
        return new VerifyApiRequestHandler.Request(issued.apiKey(), method, "/api/cns-cmd/cns-reqs", null, BODY_SHA,
                ts, nonce, RequestSignature.sign(issued.signingSecret(), canonical));
    }

    private int refused(VerifyApiRequestHandler.Request req) {
        return assertThrows(DomRuleViolation.class, () -> handler.verify(req).block()).httpStatus();
    }

    private String problem(VerifyApiRequestHandler.Request req) {
        return assertThrows(DomRuleViolation.class, () -> handler.verify(req).block()).problemType();
    }

    @Test
    void aSignedRequestGoesOnWithTheFiduciarysToken() {
        VerifyApiRequestHandler.Verified v = handler.verify(signed("POST", NOW.getEpochSecond(), "nonce-0000000001")).block();

        assertEquals("df-1", v.dfId());
        assertEquals("token-for-df-1", v.accessToken());
        assertEquals(true, v.signed());
    }

    @Test
    void theSameSignedRequestTwiceIsAReplay() {
        handler.verify(signed("POST", NOW.getEpochSecond(), "nonce-0000000002")).block();
        assertEquals("request-replayed", problem(signed("POST", NOW.getEpochSecond(), "nonce-0000000002")));
        assertEquals(409, refused(signed("POST", NOW.getEpochSecond(), "nonce-0000000002")));
    }

    @Test
    void anOldOrFutureTimestampIsRefused_andDoesntSpendTheNonce() {
        assertEquals("signature-expired", problem(signed("POST", NOW.getEpochSecond() - 301, "nonce-0000000003")));
        assertEquals("signature-expired", problem(signed("POST", NOW.getEpochSecond() + 301, "nonce-0000000003")));
        handler.verify(signed("POST", NOW.getEpochSecond() - 299, "nonce-0000000003")).block();
    }

    @Test
    void anAlteredRequestFails_andCantUseUpANonce() {
        VerifyApiRequestHandler.Request ok = signed("POST", NOW.getEpochSecond(), "nonce-0000000004");
        VerifyApiRequestHandler.Request altered = new VerifyApiRequestHandler.Request(ok.apiKey(), "POST",
                "/api/cns-cmd/cns-req-batches", null, ok.bodySha256(), ok.timestamp(), ok.nonce(), ok.signature());

        assertEquals("signature-invalid", problem(altered));
        handler.verify(ok).block();
    }

    @Test
    void unsignedWritesAreRefusedOnlyWhenSigningIsRequired() {
        VerifyApiRequestHandler.Request unsignedPost = new VerifyApiRequestHandler.Request(issued.apiKey(), "POST",
                "/api/cns-cmd/cns-reqs", null, BODY_SHA, null, null, null);
        handler.verify(unsignedPost).block();

        issued.key().requireSigning(true);
        assertEquals("signature-required", problem(unsignedPost));
        handler.verify(new VerifyApiRequestHandler.Request(issued.apiKey(), "GET", "/api/cns-qry/cns-arts", null,
                RequestSignature.sha256Hex(new byte[0]), null, null, null)).block();
    }

    @Test
    void unknownRevokedOrInactiveKeysAreRefused() {
        assertEquals("api-key-invalid", problem(new VerifyApiRequestHandler.Request("cmk_nope", "GET", "/api/x", null,
                BODY_SHA, null, null, null)));
        assertEquals("api-key-invalid", problem(new VerifyApiRequestHandler.Request(null, "GET", "/api/x", null,
                BODY_SHA, null, null, null)));
        dfs.put("df-1", DfFactory.rehydrate("df-1", "Acme", "ops@acme.example", DfPrfl.EMPTY, null, DfSts.PENDING,
                NOW, null, null));
        assertEquals(403, refused(signed("GET", NOW.getEpochSecond(), "nonce-0000000005")));
        issued.key().revoke();
        assertEquals("api-key-invalid", problem(signed("GET", NOW.getEpochSecond(), "nonce-0000000006")));
    }
}
