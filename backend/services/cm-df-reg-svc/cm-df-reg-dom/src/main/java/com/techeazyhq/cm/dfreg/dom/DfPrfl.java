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

import java.util.regex.Pattern;

/**
 * What the platform knows about a Data Fiduciary beyond its display name and contact email, captured at onboarding:
 * the registered legal entity, how to reach it, and its Data Protection Officer (DPDPA s.8(9) and s.10(2)(a): the
 * contact a data principal's grievances go to). Every field is optional; a value that is given must be well formed.
 * Blank values are stored as absent.
 */
public record DfPrfl(
        String legalName,
        String contactPhone,
        String website,
        String registeredAddress,
        String industry,
        String cin,
        String gstin,
        String dpoName,
        String dpoEmail) {

    public static final DfPrfl EMPTY = new DfPrfl(null, null, null, null, null, null, null, null, null);

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    /** E.164: a plus, then 8 to 15 digits; spaces and hyphens are dropped first. */
    private static final Pattern PHONE = Pattern.compile("^\\+[1-9][0-9]{7,14}$");
    private static final Pattern WEBSITE = Pattern.compile("^https?://[^\\s/$.?#][^\\s]*$", Pattern.CASE_INSENSITIVE);
    /** Corporate Identity Number issued by India's Ministry of Corporate Affairs. */
    private static final Pattern CIN = Pattern.compile("^[LU][0-9]{5}[A-Z]{2}[0-9]{4}[A-Z]{3}[0-9]{6}$");
    /** Goods and Services Tax Identification Number: state code, PAN, entity number, 'Z', checksum. */
    private static final Pattern GSTIN = Pattern.compile("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$");

    public DfPrfl {
        legalName = text(legalName, "legalName", 255);
        contactPhone = matching(stripped(contactPhone), "contactPhone", PHONE);
        website = matching(text(website, "website", 255), "website", WEBSITE);
        registeredAddress = text(registeredAddress, "registeredAddress", 500);
        industry = text(industry, "industry", 100);
        cin = matching(upper(cin), "cin", CIN);
        gstin = matching(upper(gstin), "gstin", GSTIN);
        dpoName = text(dpoName, "dpoName", 255);
        dpoEmail = matching(text(dpoEmail, "dpoEmail", 255), "dpoEmail", EMAIL);
    }

    /** For the fiduciary's own contact email, which lives on {@link Df} but follows the same rule. */
    static String email(String value, String field) {
        String email = matching(text(value, field, 255), field, EMAIL);
        if (email == null) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return email;
    }

    private static String text(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException(field + " must be at most " + maxLength + " characters");
        }
        return trimmed;
    }

    private static String stripped(String value) {
        return value == null ? null : value.replaceAll("[\\s-]", "");
    }

    private static String upper(String value) {
        return value == null ? null : value.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private static String matching(String value, String field, Pattern pattern) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        if (!pattern.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " is not valid");
        }
        return value;
    }
}
