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

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * A person a fiduciary asks for consent, by the contact it has for them: an email address or a mobile number. Written
 * the way spreadsheets hold them, so spaces, dashes and brackets in numbers are tolerated, and normalised away.
 */
public record Recipient(ExtDpRefTyp typ, String val) {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    /** International (+ and 8 to 15 digits) or national (8 to 15 digits) numbering. */
    private static final Pattern PHONE = Pattern.compile("^\\+?[0-9]{8,15}$");
    private static final Pattern NUMBER_PUNCTUATION = Pattern.compile("[\\s\\-().]");

    /** A contact read from a request, a file cell or a JSON field, or why it can't be one. */
    public sealed interface Parsed {
    }

    public record Ok(Recipient recipient) implements Parsed {
    }

    public record Problem(String reason) implements Parsed {
    }

    /** Recognises an email address or a mobile number; {@code typ} may force one, or be null to detect it. */
    public static Parsed parse(String raw, ExtDpRefTyp typ) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) {
            return new Problem("No email address or mobile number");
        }
        boolean looksLikeEmail = text.contains("@");
        if (typ == ExtDpRefTyp.EMAIL || (typ == null && looksLikeEmail)) {
            String email = text.toLowerCase(Locale.ROOT);
            return email.length() <= 254 && EMAIL.matcher(email).matches()
                    ? new Ok(new Recipient(ExtDpRefTyp.EMAIL, email))
                    : new Problem("Not a valid email address");
        }
        String number = NUMBER_PUNCTUATION.matcher(text).replaceAll("");
        return PHONE.matcher(number).matches()
                ? new Ok(new Recipient(ExtDpRefTyp.PHONE, number))
                : new Problem("Not a valid mobile number");
    }

    public DpRef dpRef() {
        return DpRef.unlinked(typ, val);
    }
}
