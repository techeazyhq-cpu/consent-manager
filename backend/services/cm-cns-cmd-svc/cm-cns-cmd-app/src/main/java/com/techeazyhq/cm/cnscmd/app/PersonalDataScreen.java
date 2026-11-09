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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Looks for personal data in text that is about to be sent to a model (ADR-111). A fiduciary describes its purpose in its own words;
 * those words must not contain anyone's email address, phone number, identity or card number, because the model is not where such data goes.
 * It names the kinds it found, never the text, so refusing does not repeat the data. A best effort: a name or an address in prose is not
 * recognised, and the console says so.
 */
final class PersonalDataScreen {

    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    /** An Indian PAN: five letters, four digits, a letter. */
    private static final Pattern PAN = Pattern.compile("\\b[A-Z]{5}[0-9]{4}[A-Z]\\b");
    /** An Aadhaar-shaped number: twelve digits, in groups of four or not. */
    private static final Pattern AADHAAR = Pattern.compile("\\b[2-9][0-9]{3}[ -]?[0-9]{4}[ -]?[0-9]{4}\\b");
    /** A phone number: a run of 9 or more digits, with the usual separators and an optional +. */
    private static final Pattern PHONE = Pattern.compile("(?<![0-9])\\+?[0-9][0-9 ()-]{8,}[0-9](?![0-9])");
    /** A card number: 13 to 19 digits, in groups of four or not. */
    private static final Pattern CARD = Pattern.compile("\\b(?:[0-9][ -]?){13,19}\\b");

    private PersonalDataScreen() {
    }

    /** The kinds of personal data in the text, by name: empty when none was recognised. */
    static List<String> find(String text) {
        List<String> kinds = new ArrayList<>();
        if (text == null) {
            return kinds;
        }
        if (EMAIL.matcher(text).find()) {
            kinds.add("an email address");
        }
        if (PAN.matcher(text).find()) {
            kinds.add("a PAN");
        }
        if (AADHAAR.matcher(text).find()) {
            kinds.add("an Aadhaar-shaped number");
        }
        if (CARD.matcher(text).find() && luhnRun(text)) {
            kinds.add("a card number");
        }
        if (PHONE.matcher(text).find() && !kinds.contains("an Aadhaar-shaped number") && !kinds.contains("a card number")) {
            kinds.add("a phone number");
        }
        return kinds;
    }

    /** True when some run of 13 to 19 digits in the text passes the Luhn check, as a real card number does. */
    private static boolean luhnRun(String text) {
        var m = CARD.matcher(text);
        while (m.find()) {
            String digits = m.group().replaceAll("[^0-9]", "");
            if (digits.length() >= 13 && digits.length() <= 19 && luhn(digits)) {
                return true;
            }
        }
        return false;
    }

    private static boolean luhn(String digits) {
        int sum = 0;
        boolean alt = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int n = digits.charAt(i) - '0';
            if (alt) {
                n *= 2;
                if (n > 9) {
                    n -= 9;
                }
            }
            sum += n;
            alt = !alt;
        }
        return sum % 10 == 0;
    }
}
