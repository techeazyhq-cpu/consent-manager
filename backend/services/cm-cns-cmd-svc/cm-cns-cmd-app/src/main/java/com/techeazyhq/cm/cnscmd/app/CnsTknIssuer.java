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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.techeazyhq.cm.cnscmd.dom.CnsArt;
import com.techeazyhq.cm.cnscmd.dom.DCat;
import com.techeazyhq.cm.cnscmd.dom.DpRef;
import com.techeazyhq.cm.seckit.CnsTknClaims;
import com.techeazyhq.cm.seckit.CnsTknSigner;

/**
 * Signs consent tokens that live for {@code lifetime}, never past the artefact's validity (ADR-027). A token's lifetime
 * is the longest a verifier holding it can keep honouring a withdrawn consent offline, so it is kept short; fiduciaries
 * get a fresh one through {@link RefreshCnsTknCmdHandler} while the consent is valid.
 */
public class CnsTknIssuer {

    private final CnsTknSigner signer;
    private final Duration lifetime;
    private final Clock clock;
    private final com.techeazyhq.cm.cnscmd.dom.StatusSlotPort slots;
    private final String statusListBaseUrl;

    /** Tokens without a status list place (for tests and for a deployment that has not set up its address). */
    public CnsTknIssuer(CnsTknSigner signer, Duration lifetime, Clock clock) {
        this(signer, lifetime, clock, null, null);
    }

    /** Tokens that name their place in the fiduciary's status list (ADR-077), at {@code statusListBaseUrl}/api/cns-cmd/status-lists/{df}. */
    public CnsTknIssuer(CnsTknSigner signer, Duration lifetime, Clock clock,
                        com.techeazyhq.cm.cnscmd.dom.StatusSlotPort slots, String statusListBaseUrl) {
        if (lifetime.isNegative() || lifetime.isZero()) {
            throw new IllegalArgumentException("Consent token lifetime must be positive");
        }
        this.signer = signer;
        this.lifetime = lifetime;
        this.clock = clock;
        this.slots = slots;
        this.statusListBaseUrl = statusListBaseUrl == null ? null : withoutTrailingSlashes(statusListBaseUrl);
    }

    private static String withoutTrailingSlashes(String url) {
        int end = url.length();
        while (end > 0 && url.charAt(end - 1) == '/') {
            end--;
        }
        return url.substring(0, end);
    }

    /** Where a fiduciary's status list is served; the address a token's {@code status} claim names. */
    public static String statusListUri(String baseUrl, String dfId) {
        return baseUrl + "/api/cns-cmd/status-lists/" + dfId;
    }

    /** As {@link #issue}, with the token's place in the status list when that is set up. */
    public reactor.core.publisher.Mono<Issued> issueFor(CnsArt cnsArt) {
        if (slots == null || statusListBaseUrl == null || statusListBaseUrl.isBlank()) {
            return reactor.core.publisher.Mono.fromSupplier(() -> issue(cnsArt));
        }
        return slots.slotFor(cnsArt.dfId(), cnsArt.id()).map(idx -> sign(cnsArt,
                new CnsTknClaims.StatusRef(statusListUri(statusListBaseUrl, cnsArt.dfId()), idx)));
    }

    public Issued issue(CnsArt cnsArt) {
        return sign(cnsArt, null);
    }

    private Issued sign(CnsArt cnsArt, CnsTknClaims.StatusRef status) {
        Instant now = clock.instant();
        Instant byLifetime = now.plus(lifetime);
        Instant exp = cnsArt.valPrd().until().isBefore(byLifetime) ? cnsArt.valPrd().until() : byLifetime;
        // the data the consent covers (ADR-026): with partial consent, fewer categories than were asked
        List<String> scope = cnsArt.scp().dCats().stream().map(DCat::code).sorted().toList();
        String token = signer.sign(new CnsTknClaims(cnsArt.id(), dpIdOf(cnsArt), cnsArt.dfId(), cnsArt.prps().code(),
                CnsTknClaims.AccessType.ALLOW, scope, exp, status));
        return new Issued(token, exp);
    }

    public record Issued(String token, Instant exp) {
    }

    private static String dpIdOf(CnsArt cnsArt) {
        if (cnsArt.dpRef() instanceof DpRef.LnkdDpRef linked) {
            return linked.dpId();
        }
        throw new IllegalStateException("Cannot issue a token for an artefact whose DP is still unlinked");
    }
}
