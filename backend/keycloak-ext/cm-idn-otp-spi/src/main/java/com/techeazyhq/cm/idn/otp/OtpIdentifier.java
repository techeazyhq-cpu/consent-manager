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

package com.techeazyhq.cm.idn.otp;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * What a person signs in with: an email address or a phone number in international (E.164) form. Only these shapes
 * are accepted, so the value is safe to send to cm-ntf-svc and to show back, masked, on the code page.
 */
public record OtpIdentifier(Kind kind, String value) {

    public enum Kind {
        EMAIL,
        PHONE
    }

    private static final Pattern EMAIL = Pattern.compile("[a-z0-9._%+-]{1,64}@[a-z0-9-]+(\\.[a-z0-9-]+)*\\.[a-z]{2,24}");
    private static final Pattern PHONE = Pattern.compile("\\+[1-9][0-9]{7,14}");
    private static final Pattern PHONE_PUNCTUATION = Pattern.compile("[\\s().-]");

    public static Optional<OtpIdentifier> parse(String input) {
        if (input == null) {
            return Optional.empty();
        }
        String trimmed = input.trim();
        String email = trimmed.toLowerCase(Locale.ROOT);
        if (email.length() <= 254 && EMAIL.matcher(email).matches()) {
            return Optional.of(new OtpIdentifier(Kind.EMAIL, email));
        }
        String phone = PHONE_PUNCTUATION.matcher(trimmed).replaceAll("");
        if (PHONE.matcher(phone).matches()) {
            return Optional.of(new OtpIdentifier(Kind.PHONE, phone));
        }
        return Optional.empty();
    }

    /** The channel cm-ntf-svc delivers the code over ({@code NtfChnl}). */
    public String channel() {
        return kind == Kind.EMAIL ? "EMAIL" : "SMS";
    }

    /** Enough to recognise the address without disclosing it to someone looking over a shoulder: a•••@example.com. */
    public String masked() {
        if (kind == Kind.EMAIL) {
            int at = value.indexOf('@');
            return value.charAt(0) + "•••" + value.substring(at);
        }
        return value.substring(0, 3) + "•••" + value.substring(value.length() - 3);
    }
}
