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
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * A party that handles personal data on a fiduciary's behalf or receives it (ADR-075): a courier, a payment provider, a
 * cloud host. The register is the fiduciary's own list; a purpose version names a processor by its name in its recipients or
 * transfers. A processor that is no longer used is retired, not deleted, so versions that named it still read true.
 *
 * @param role    what it does with the data, in a sentence, as a principal would want to read it
 * @param country ISO 3166-1 alpha-2 of where it processes the data, or empty if not stated
 */
public record Processor(String id, String dfId, String name, String role, String country, String contactEmail,
                        Status sts, Instant createdAt, Instant retiredAt) {

    public enum Status { ACTIVE, RETIRED }

    public static final int MAX_NAME = 200;
    public static final int MAX_ROLE = 500;
    private static final Pattern COUNTRY = Pattern.compile("^[A-Z]{2}$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    /** A new, active processor. Throws {@link IllegalArgumentException} saying what is wrong, first problem first. */
    public static Processor register(String dfId, String name, String role, String country, String contactEmail, Instant now) {
        String n = clean(name);
        if (n.isEmpty() || n.length() > MAX_NAME) {
            throw new IllegalArgumentException("a processor has a name of up to " + MAX_NAME + " characters");
        }
        return new Processor(UUID.randomUUID().toString(), dfId, n, tidyRole(role), tidyCountry(country), tidyEmail(contactEmail),
                Status.ACTIVE, now, null);
    }

    /** The same processor with what the fiduciary changed; its name and status stay. */
    public Processor describe(String newRole, String newCountry, String newContactEmail) {
        return new Processor(id, dfId, name, tidyRole(newRole), tidyCountry(newCountry), tidyEmail(newContactEmail), sts,
                createdAt, retiredAt);
    }

    public Processor retire(Instant now) {
        return sts == Status.RETIRED ? this : new Processor(id, dfId, name, role, country, contactEmail, Status.RETIRED, createdAt, now);
    }

    public Processor reinstate() {
        return sts == Status.ACTIVE ? this : new Processor(id, dfId, name, role, country, contactEmail, Status.ACTIVE, createdAt, null);
    }

    public boolean active() {
        return sts == Status.ACTIVE;
    }

    /** How purposes compare names: ignoring case and surrounding space. */
    public static String key(String name) {
        return name == null ? "" : clean(name).toLowerCase(Locale.ROOT);
    }

    private static String clean(String s) {
        return s == null ? "" : s.strip().replaceAll("\\s+", " ");
    }

    private static String tidyRole(String s) {
        String r = clean(s);
        if (r.length() > MAX_ROLE) {
            throw new IllegalArgumentException("say what it does in up to " + MAX_ROLE + " characters");
        }
        return r;
    }

    private static String tidyCountry(String s) {
        String c = s == null ? "" : s.strip().toUpperCase(Locale.ROOT);
        if (!c.isEmpty() && !COUNTRY.matcher(c).matches()) {
            throw new IllegalArgumentException("the country is a two-letter ISO 3166 code, such as IN or DE");
        }
        return c;
    }

    private static String tidyEmail(String s) {
        String e = s == null ? "" : s.strip();
        if (!e.isEmpty() && (e.length() > 254 || !EMAIL.matcher(e).matches())) {
            throw new IllegalArgumentException("the contact must be an email address");
        }
        return e;
    }
}
