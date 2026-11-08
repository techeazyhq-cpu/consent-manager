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

import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.Deflater;

import com.techeazyhq.cm.cnscmd.dom.StatusSlotPort;
import com.techeazyhq.cm.seckit.CnsTknSigner;

import reactor.core.publisher.Mono;

/**
 * A fiduciary's status list (ADR-077): one bit per consent token it has been issued, set once the consent has ended, signed
 * with the token keys and served to anyone, because an index says nothing about whose consent it is. A verifier that has
 * checked a token's signature looks its index up here instead of asking the Consent Manager about every use; the list says
 * how long it may be used ({@code ttl}), and a list older than that is not an answer.
 *
 * <p>The list is built from the live state of the fiduciary's consents and kept for {@code cacheFor}, so an unauthenticated
 * request costs one query per fiduciary per that interval, not one per request.
 */
public class StatusLists {

    /** Bit {@code idx} is bit {@code idx % 8} of byte {@code idx / 8}, least significant first, as the IETF Token Status List says. */
    public static byte[] bitString(Iterable<StatusSlotPort.Entry> entries, int sizeBits) {
        byte[] bytes = new byte[(sizeBits + 7) / 8];
        for (StatusSlotPort.Entry e : entries) {
            if (e.ended() && e.idx() >= 0 && e.idx() < sizeBits) {
                bytes[e.idx() / 8] |= (byte) (1 << (e.idx() % 8));
            }
        }
        return bytes;
    }

    /** zlib (RFC 1950), as the format asks, then base64url without padding. */
    public static String compress(byte[] bits) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        deflater.setInput(bits);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        while (!deflater.finished()) {
            out.write(buf, 0, deflater.deflate(buf));
        }
        deflater.end();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray());
    }

    /** More fiduciaries than this have never been served at once; past it the cache starts over rather than grow. */
    private static final int MAX_CACHED = 1_000;

    private record Cached(String token, Instant until) { }

    private final StatusSlotPort slots;
    private final CnsTknSigner signer;
    private final String baseUrl;
    private final Duration ttl;
    private final Duration cacheFor;
    private final Clock clock;
    private final ConcurrentHashMap<String, Cached> cache = new ConcurrentHashMap<>();

    public StatusLists(StatusSlotPort slots, CnsTknSigner signer, String baseUrl, Duration ttl, Duration cacheFor, Clock clock) {
        this.slots = slots;
        this.signer = signer;
        this.baseUrl = baseUrl;
        this.ttl = ttl;
        this.cacheFor = cacheFor;
        this.clock = clock;
    }

    public Duration ttl() {
        return ttl;
    }

    /** The signed list for a fiduciary: all zeros for one that has issued nothing. */
    public Mono<String> tokenFor(String dfId) {
        Cached hit = cache.get(dfId);
        if (hit != null && hit.until().isAfter(clock.instant())) {
            return Mono.just(hit.token());
        }
        return slots.entries(dfId).collectList().map(entries -> {
            Instant now = clock.instant();
            String token = signer.signStatusList(CnsTknIssuer.statusListUri(baseUrl, dfId),
                    compress(bitString(entries, StatusSlotPort.LIST_SIZE)), now, ttl);
            // only a fiduciary that has issued tokens is kept: an open endpoint must not let anyone fill memory with made-up ids
            if (!entries.isEmpty()) {
                if (cache.size() >= MAX_CACHED) {
                    cache.values().removeIf(c -> !c.until().isAfter(now));
                    if (cache.size() >= MAX_CACHED) {
                        cache.clear();
                    }
                }
                cache.put(dfId, new Cached(token, now.plus(cacheFor)));
            }
            return token;
        });
    }
}
