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

import java.time.Instant;

/** Validity period of a {@link CnsArt}; {@code until} maps to the external {@code exp} claim. */
public record ValPrd(Instant from, Instant until) {
    public ValPrd {
        if (from == null || until == null) {
            throw new IllegalArgumentException("from/until must not be null");
        }
        if (!until.isAfter(from)) {
            throw new IllegalArgumentException("until must be after from");
        }
    }

    public boolean isExpiredAt(Instant instant) {
        return instant.isAfter(until);
    }

    public static ValPrd of(Instant from, java.time.Duration duration) {
        return new ValPrd(from, from.plus(duration));
    }
}
