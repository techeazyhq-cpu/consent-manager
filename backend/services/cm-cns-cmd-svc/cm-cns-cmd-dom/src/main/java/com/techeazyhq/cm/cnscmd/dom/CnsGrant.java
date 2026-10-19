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

package com.techeazyhq.cm.cnscmd.dom;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What a principal gives when they consent (ADR-026): some or all of the data categories the request asked for, for
 * a validity they choose. Partial consent records exactly the categories granted; the validity opens on what the
 * fiduciary requested and may be shorter, or longer up to the purpose's maximum, never more.
 *
 * @param scp    the categories granted
 * @param valPrd from now for the chosen number of days
 */
public record CnsGrant(Scp scp, ValPrd valPrd, int validityDays) {

    /**
     * The validity every consent had before purposes carried their own: used only for a request stored without a
     * purpose version, which the V3 migration left none of.
     */
    public static final int LEGACY_VALIDITY_DAYS = 365;

    /**
     * @param asked     what the request asked for
     * @param terms     the purpose version it was made from; null only for a request without one
     * @param dCatCodes the categories the principal ticked; all that were asked when null
     * @param days      the validity they chose; the requested one when null
     */
    public static CnsGrant of(Scp asked, PrpsVerContent terms, Collection<String> dCatCodes, Integer days,
                              Instant now) {
        Set<DCat> chosen = dCatCodes == null ? asked.dCats()
                : dCatCodes.stream().filter(c -> c != null && !c.isBlank()).map(String::strip).map(DCat::new)
                        .collect(Collectors.toSet());
        if (chosen.isEmpty()) {
            throw new IllegalArgumentException("choose at least one kind of data to give consent for, or decline");
        }
        List<String> notAsked = chosen.stream().filter(d -> !asked.covers(d)).map(DCat::code).sorted().toList();
        if (!notAsked.isEmpty()) {
            throw new IllegalArgumentException("the request didn't ask for " + String.join(", ", notAsked));
        }

        int requested = terms == null ? LEGACY_VALIDITY_DAYS : terms.requestedValidityDays();
        int max = terms == null ? LEGACY_VALIDITY_DAYS : terms.maxValidityDays();
        int validity = days == null ? requested : days;
        if (validity < 1 || validity > max) {
            throw new IllegalArgumentException("consent can last from 1 to " + max + " days for this purpose");
        }
        return new CnsGrant(new Scp(chosen), ValPrd.of(now, Duration.ofDays(validity)), validity);
    }

    /** Whether the principal gave less than was asked. */
    public boolean isPartialOf(Scp asked) {
        return !scp.dCats().equals(asked.dCats());
    }
}
