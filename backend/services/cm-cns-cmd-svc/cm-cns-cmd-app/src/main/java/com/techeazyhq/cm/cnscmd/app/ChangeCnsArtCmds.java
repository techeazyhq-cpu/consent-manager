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
import java.time.Instant;
import java.util.function.Consumer;
import java.util.function.Function;

import org.springframework.transaction.annotation.Transactional;

import com.techeazyhq.cm.cnscmd.dom.CnsArt;
import com.techeazyhq.cm.cnscmd.dom.CnsArtRepoPort;
import com.techeazyhq.cm.cnscmd.dom.CnsGrant;
import com.techeazyhq.cm.cnscmd.dom.CnsReqRepoPort;
import com.techeazyhq.cm.cnscmd.dom.DCat;
import com.techeazyhq.cm.cnscmd.dom.PrpsVerContent;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Mono;

/**
 * What a principal changes about a consent they gave (ADR-026 §4): end it earlier, withdraw one category, or renew it
 * before it lapses. Only the principal the consent names can; anyone else is told it doesn't exist. Each change is a
 * 27560 event the fiduciary receives (CnsModifiedEvt, CnsRenewedEvt).
 */
public class ChangeCnsArtCmds {

    private final CnsArtRepoPort arts;
    private final CnsReqRepoPort reqs;
    private final CnsReqTerms terms;
    private final Clock clock;

    public ChangeCnsArtCmds(CnsArtRepoPort arts, CnsReqRepoPort reqs, CnsReqTerms terms, Clock clock) {
        this.arts = arts;
        this.reqs = reqs;
        this.terms = terms;
        this.clock = clock;
    }

    /** Ends the consent at {@code until}, earlier than it would. */
    @Transactional
    public Mono<CnsArt> shorten(String cnsArtId, String dpId, Instant until) {
        if (until == null) {
            return Mono.error(new DomRuleViolation(400, "invalid-change", "Say when the consent should end: until"));
        }
        return change(cnsArtId, dpId, art -> art.shortenTo(until, clock.instant()));
    }

    /** Stops sharing one category; the rest of the consent stays. */
    @Transactional
    public Mono<CnsArt> withdrawCategory(String cnsArtId, String dpId, String dCatCode) {
        if (dCatCode == null || dCatCode.isBlank()) {
            return Mono.error(new DomRuleViolation(400, "invalid-change", "Name the data to stop sharing: dCatCode"));
        }
        return change(cnsArtId, dpId, art -> art.withdrawCategory(new DCat(dCatCode.strip()), clock.instant()));
    }

    /** A new validity of {@code validityDays} from now, up to the purpose version's maximum. */
    @Transactional
    public Mono<CnsArt> renew(String cnsArtId, String dpId, Integer validityDays) {
        if (validityDays == null) {
            return Mono.error(new DomRuleViolation(400, "invalid-change", "Say how many days to renew for: validityDays"));
        }
        return own(cnsArtId, dpId).flatMap(art -> maxValidityDays(art).flatMap(max ->
                apply(art, a -> a.renew(validityDays, max, clock.instant()))));
    }

    private Mono<CnsArt> change(String cnsArtId, String dpId, Consumer<CnsArt> action) {
        return own(cnsArtId, dpId).flatMap(art -> apply(art, action));
    }

    private Mono<CnsArt> apply(CnsArt art, Consumer<CnsArt> action) {
        try {
            action.accept(art);
        } catch (IllegalArgumentException e) {
            return Mono.error(new DomRuleViolation(400, "invalid-change", e.getMessage()));
        } catch (IllegalStateException e) {
            return Mono.error(new DomRuleViolation(409, "cns-art-not-in-force", e.getMessage()));
        }
        return arts.save(art);
    }

    /** The longest this consent's purpose allows (ADR-026); a consent from before purpose versions keeps the old year. */
    private Mono<Integer> maxValidityDays(CnsArt art) {
        Function<PrpsVerContent, Integer> max = PrpsVerContent::maxValidityDays;
        return reqs.findById(art.cnsReqId())
                .flatMap(terms::versionOf)
                .map(max)
                .defaultIfEmpty(CnsGrant.LEGACY_VALIDITY_DAYS);
    }

    private Mono<CnsArt> own(String cnsArtId, String dpId) {
        return arts.findById(cnsArtId)
                .filter(art -> art.dpRef().isOf(dpId))
                .switchIfEmpty(Mono.error(() -> new DomRuleViolation(404, "cns-art-not-found", "No CnsArt " + cnsArtId)));
    }
}
