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

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * What one version of a purpose says (ADR-031): the data it covers, who receives it, how long it is kept, how long
 * consent to it may last (ADR-026), and the notice in each language.
 *
 * <p>The constructor takes values as they are, as stored versions are read back with it; {@link #check()} holds the
 * rules a new draft must keep to. A stored version that no longer passes today's checks must still be readable.
 *
 * @param requestedValidityDays how long the fiduciary asks consent to last; the decision screen opens on it
 * @param maxValidityDays       the longest the fiduciary can justify; the principal may choose up to it
 */
public record PrpsVerContent(String name, String description, List<PrpsDCat> dCats, List<String> recipients,
                             List<String> transfers, int retentionDays, int requestedValidityDays, int maxValidityDays,
                             Set<PrpsFlag> flags, List<PrpsNtc> notices) {

    /**
     * The longest consent may last, until regime profiles (ADR-020) set it per law and purpose. Endless consent is
     * never accepted (ADR-026 §1).
     */
    public static final int MAX_VALIDITY_DAYS = 3650;
    /** The longest retention a purpose may state: data kept beyond a century is kept indefinitely in all but name. */
    public static final int MAX_RETENTION_DAYS = 36_500;
    public static final int MAX_NAME = 120;
    public static final int MAX_DESCRIPTION = 2_000;
    public static final int MAX_CONSEQUENCE = 1_000;
    public static final int MAX_PARTY = 200;
    public static final int MAX_PARTIES = 20;
    public static final int MAX_NOTICES = 30;
    public static final int MAX_DPV = 128;

    /** Codes as tokens carry them: capitals, digits and underscores, starting with a letter. */
    static final Pattern CODE = Pattern.compile("^[A-Z][A-Z0-9_]{0,63}$");
    /** A BCP 47 tag, as far as notices need one: a language, then optional script or region subtags. */
    private static final Pattern LANG = Pattern.compile("^[a-z]{2,3}(-[A-Za-z0-9]{2,8}){0,3}$");
    /** The language a request falls back to when it names none and the purpose has a notice in it. */
    public static final String DEFAULT_LANG = "en";

    public PrpsVerContent {
        dCats = present(dCats);
        recipients = present(recipients);
        transfers = present(transfers);
        flags = flags == null || flags.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(flags));
        notices = present(notices);
    }

    /** A JSON list may hold nulls; they say nothing, so they are dropped rather than failing as a server error. */
    private static <T> List<T> present(List<T> list) {
        return list == null ? List.of() : list.stream().filter(Objects::nonNull).toList();
    }

    /** Throws {@link IllegalArgumentException} saying what a new draft gets wrong, first problem first. */
    public void check() {
        requireText(name, "name", MAX_NAME, false);
        if (description != null && !description.isEmpty()) {
            requireText(description, "description", MAX_DESCRIPTION, true);
        }

        if (dCats.isEmpty()) {
            throw new IllegalArgumentException("a purpose covers at least one data category");
        }
        if (dCats.size() > CnsReqFactory.MAX_DCATS) {
            throw new IllegalArgumentException("a purpose may cover at most " + CnsReqFactory.MAX_DCATS
                    + " data categories");
        }
        Set<String> codes = new HashSet<>();
        for (PrpsDCat d : dCats) {
            if (d == null || d.code() == null || !CODE.matcher(d.code()).matches()) {
                throw new IllegalArgumentException("data category codes are capitals, digits and underscores, "
                        + "starting with a letter, at most 64 characters");
            }
            if (!codes.add(d.code())) {
                throw new IllegalArgumentException("data category " + d.code() + " is listed twice");
            }
            if (d.dpv() != null && !d.dpv().isEmpty()) {
                requireText(d.dpv(), "the DPV type of " + d.code(), MAX_DPV, false);
            }
            if (d.necessary()) {
                if (d.consequence() == null || d.consequence().isBlank()) {
                    throw new IllegalArgumentException("say what happens if someone refuses " + d.code()
                            + ": it is marked necessary");
                }
                requireText(d.consequence(), "the consequence for " + d.code(), MAX_CONSEQUENCE, true);
            } else if (d.consequence() != null && !d.consequence().isEmpty()) {
                requireText(d.consequence(), "the consequence for " + d.code(), MAX_CONSEQUENCE, true);
            }
        }

        requireParties(recipients, "recipients");
        requireParties(transfers, "transfers");

        if (retentionDays < 1 || retentionDays > MAX_RETENTION_DAYS) {
            throw new IllegalArgumentException("retention is from 1 to " + MAX_RETENTION_DAYS + " days");
        }
        if (maxValidityDays < 1 || maxValidityDays > MAX_VALIDITY_DAYS) {
            throw new IllegalArgumentException("the longest validity is from 1 to " + MAX_VALIDITY_DAYS + " days");
        }
        if (requestedValidityDays < 1 || requestedValidityDays > maxValidityDays) {
            throw new IllegalArgumentException("the requested validity is from 1 day up to the longest validity");
        }

        if (notices.isEmpty()) {
            throw new IllegalArgumentException("write the notice in at least one language");
        }
        if (notices.size() > MAX_NOTICES) {
            throw new IllegalArgumentException("a purpose may have notices in at most " + MAX_NOTICES + " languages");
        }
        Set<String> langs = new HashSet<>();
        for (PrpsNtc n : notices) {
            if (n == null || n.lang() == null || !LANG.matcher(n.lang()).matches()) {
                throw new IllegalArgumentException("a notice's language is a tag such as en, hi or ta-IN");
            }
            if (!langs.add(n.lang())) {
                throw new IllegalArgumentException("there are two notices in " + n.lang());
            }
            requireText(n.title(), "the " + n.lang() + " notice's title", CnsReqFactory.MAX_NTC_TITLE, false);
            requireText(n.text(), "the " + n.lang() + " notice", CnsReqFactory.MAX_NTC_TEXT, true);
        }
    }

    public Optional<PrpsDCat> dCat(String code) {
        return dCats.stream().filter(d -> d.code().equals(code)).findFirst();
    }

    /** The notice in {@code lang}; with none named, English if there is one, otherwise the first written. */
    public Optional<PrpsNtc> notice(String lang) {
        if (lang == null || lang.isBlank()) {
            return notices.stream().filter(n -> n.lang().equals(DEFAULT_LANG)).findFirst()
                    .or(() -> notices.stream().findFirst());
        }
        return notices.stream().filter(n -> n.lang().equals(lang)).findFirst();
    }

    private static void requireParties(List<String> parties, String what) {
        if (parties.size() > MAX_PARTIES) {
            throw new IllegalArgumentException("list at most " + MAX_PARTIES + " " + what);
        }
        if (new HashSet<>(parties).size() != parties.size()) {
            throw new IllegalArgumentException("one of the " + what + " is listed twice");
        }
        for (String p : parties) {
            requireText(p, "each of the " + what, MAX_PARTY, false);
        }
    }

    private static void requireText(String value, String what, int max, boolean lineBreaks) {
        if (value == null || value.isBlank() || !value.equals(value.strip())) {
            throw new IllegalArgumentException(what + " must not be blank or start or end with spaces");
        }
        if (value.length() > max) {
            throw new IllegalArgumentException(what + " must be at most " + max + " characters");
        }
        if (CnsReqFactory.hasControl(value, lineBreaks)) {
            throw new IllegalArgumentException("control characters aren't allowed in " + what);
        }
    }
}
