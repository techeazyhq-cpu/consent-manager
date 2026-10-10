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

package com.techeazyhq.cm.domkit;

/**
 * Specification pattern base (plan §12) for composable domain business rules, e.g.
 * {@code CnsReqDecidablePolicy}, {@code DfSubEligiblePolicy}, {@code CnsArtValidPolicy}.
 * Implementations return a reason on failure rather than a bare boolean, since every
 * rejection in this platform must map to a specific {@code problem+json} type.
 */
public interface Spec<T> {

    SpecResult evaluate(T candidate);

    record SpecResult(boolean satisfied, String reasonCode) {
        public static SpecResult ok() {
            return new SpecResult(true, null);
        }

        public static SpecResult violated(String reasonCode) {
            return new SpecResult(false, reasonCode);
        }
    }
}
