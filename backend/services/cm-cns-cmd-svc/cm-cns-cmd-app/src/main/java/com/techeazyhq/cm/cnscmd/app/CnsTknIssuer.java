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

    public CnsTknIssuer(CnsTknSigner signer, Duration lifetime, Clock clock) {
        if (lifetime.isNegative() || lifetime.isZero()) {
            throw new IllegalArgumentException("Consent token lifetime must be positive");
        }
        this.signer = signer;
        this.lifetime = lifetime;
        this.clock = clock;
    }

    public Issued issue(CnsArt cnsArt) {
        Instant now = clock.instant();
        Instant byLifetime = now.plus(lifetime);
        Instant exp = cnsArt.valPrd().until().isBefore(byLifetime) ? cnsArt.valPrd().until() : byLifetime;
        String token = signer.sign(new CnsTknClaims(cnsArt.id(), dpIdOf(cnsArt), cnsArt.dfId(), cnsArt.prps().code(),
                CnsTknClaims.AccessType.ALLOW, List.of(), exp));
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
