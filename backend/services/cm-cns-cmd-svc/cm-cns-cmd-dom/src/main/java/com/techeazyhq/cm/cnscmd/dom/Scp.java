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

import java.util.Set;

/** The set of {@link DCat} a {@link CnsArt} authorizes access to — external claim name {@code scope}. */
public record Scp(Set<DCat> dCats) {
    public Scp {
        if (dCats == null || dCats.isEmpty()) {
            throw new IllegalArgumentException("scope must contain at least one data category");
        }
        dCats = Set.copyOf(dCats);
    }

    public boolean covers(DCat dCat) {
        return dCats.contains(dCat);
    }
}
