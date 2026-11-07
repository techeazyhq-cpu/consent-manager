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


package com.techeazyhq.cm.dfreg.dom;

import java.util.Locale;
import java.util.Optional;

/** The consent manager's own documents a fiduciary accepts before it goes live (ADR-063). */
public enum TermsDoc {

    /** The terms of service for using the consent manager. */
    TERMS,
    /** The data processing agreement: the consent manager processes personal data for the fiduciary. */
    DPA;

    /** Reads the name used in the API ({@code terms}, {@code dpa}), in any case. */
    public static Optional<TermsDoc> parse(String name) {
        if (name == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(name.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
