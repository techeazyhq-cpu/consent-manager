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
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;

import com.techeazyhq.cm.dfreg.dom.DfAccessTokenPort;
import com.techeazyhq.cm.dfreg.dom.DfApiKey;
import com.techeazyhq.cm.dfreg.dom.DfApiKeyRepoPort;
import com.techeazyhq.cm.dfreg.dom.DfRepoPort;
import com.techeazyhq.cm.dfreg.dom.DfSts;
import com.techeazyhq.cm.dfreg.dom.RequestNoncePort;
import com.techeazyhq.cm.dfreg.dom.RequestSignature;
import com.techeazyhq.cm.dfreg.dom.SecretSealerPort;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Mono;

/**
 * Checks one API-key request for the gateway (ADR-041), in this order, and answers with an access token for the
 * fiduciary's API client, which the gateway forwards in place of the key:
 * <ol>
 *   <li>the key exists, isn't revoked, and its fiduciary is active (401 {@code api-key-invalid}, 403 {@code df-not-active});</li>
 *   <li>if the request carries any signature header, the signature: well formed, within the time window, matching
 *   (401 {@code signature-invalid}, {@code signature-expired});</li>
 *   <li>then its nonce is claimed for the key until the window closes (409 {@code request-replayed}). Only after the
 *   signature checks out, so forged requests can't use up a client's nonces;</li>
 *   <li>an unsigned write by a key that requires signing is refused (401 {@code signature-required}). A replayed read
 *   changes nothing, so reads may go unsigned.</li>
 * </ol>
 * The gateway hashes the body and sends only the hash: the body never comes here.
 */
public class VerifyApiRequestHandler {

    public static final Duration WINDOW = Duration.ofMinutes(5);
    private static final Set<String> WRITES = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final DfApiKeyRepoPort keys;
    private final DfRepoPort dfs;
    private final RequestNoncePort nonces;
    private final SecretSealerPort sealer;
    private final DfAccessTokenPort tokens;
    private final Clock clock;

    public VerifyApiRequestHandler(DfApiKeyRepoPort keys, DfRepoPort dfs, RequestNoncePort nonces,
                                   SecretSealerPort sealer, DfAccessTokenPort tokens, Clock clock) {
        this.keys = keys;
        this.dfs = dfs;
        this.nonces = nonces;
        this.sealer = sealer;
        this.tokens = tokens;
        this.clock = clock;
    }

    public record Request(String apiKey, String method, String path, String query, String bodySha256,
                          String timestamp, String nonce, String signature) {

        boolean signed() {
            return timestamp != null || nonce != null || signature != null;
        }
    }

    public record Verified(String dfId, String keyId, String accessToken, boolean signed) {
    }

    public Mono<Verified> verify(Request req) {
        if (req.apiKey() == null || !req.apiKey().startsWith(DfApiKey.KEY_PREFIX) || req.method() == null
                || req.path() == null || !RequestSignature.isHex64(req.bodySha256())) {
            return Mono.error(invalidKey());
        }
        String method = req.method().toUpperCase(Locale.ROOT);
        Instant now = clock.instant();
        return keys.findByHash(DfApiKey.hash(req.apiKey()))
                .filter(key -> !key.isRevoked())
                .switchIfEmpty(Mono.error(invalidKey()))
                .flatMap(key -> dfs.findById(key.dfId())
                        .filter(df -> df.sts() == DfSts.ACTIVE)
                        .switchIfEmpty(Mono.error(new DomRuleViolation(403, "df-not-active",
                                "The fiduciary this key belongs to isn't active")))
                        .flatMap(df -> checkSignature(key, req, method, now)
                                .then(tokens.tokenFor(df.id(), df.cmDfApiClientSecret()))
                                .flatMap(token -> keys.touch(key.id(), now).onErrorResume(e -> Mono.empty())
                                        .thenReturn(new Verified(df.id(), key.id(), token, req.signed())))));
    }

    private Mono<Void> checkSignature(DfApiKey key, Request req, String method, Instant now) {
        if (!req.signed()) {
            return key.signingRequired() && WRITES.contains(method)
                    ? Mono.error(new DomRuleViolation(401, "signature-required",
                            "This key requires signed requests: send X-Signature-Timestamp, X-Signature-Nonce and X-Signature"))
                    : Mono.empty();
        }
        if (!RequestSignature.isWellFormedTimestamp(req.timestamp()) || !RequestSignature.isWellFormedNonce(req.nonce())
                || !RequestSignature.isHex64(req.signature())) {
            return Mono.error(new DomRuleViolation(401, "signature-invalid",
                    "X-Signature-Timestamp (Unix seconds), X-Signature-Nonce (16 to 64 of A-Z a-z 0-9 _ -) and "
                            + "X-Signature (64 lower-case hex) are all needed"));
        }
        Instant at = Instant.ofEpochSecond(Long.parseLong(req.timestamp()));
        if (Duration.between(at, now).abs().compareTo(WINDOW) > 0) {
            return Mono.error(new DomRuleViolation(401, "signature-expired",
                    "X-Signature-Timestamp is more than " + WINDOW.toMinutes() + " minutes from the server's clock"));
        }
        String canonical = RequestSignature.canonical(req.timestamp(), req.nonce(), method, req.path(), req.query(),
                req.bodySha256());
        if (!RequestSignature.matches(sealer.open(key.sealedSigningSecret()), canonical, req.signature())) {
            return Mono.error(new DomRuleViolation(401, "signature-invalid", "The signature doesn't match the request"));
        }
        return nonces.claim(key.id(), req.nonce(), at.plus(WINDOW))
                .flatMap(claimed -> claimed ? Mono.<Void>empty() : Mono.error(new DomRuleViolation(409,
                        "request-replayed", "This nonce was already used: sign every request, retries too, with a new one")));
    }

    private static DomRuleViolation invalidKey() {
        return new DomRuleViolation(401, "api-key-invalid", "The API key is missing, unknown or revoked");
    }
}
