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

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Who a fiduciary may ask for consent here. Production asks anyone. The sandbox (ADR-034, ADR-037) holds no
 * personal data, so it takes only contacts that can't belong to a real person: addresses at the reserved
 * {@code sandbox.example} domain (RFC 2606 reserves {@code .example}), and the UK's numbers kept for drama
 * ({@code +44 7700 900000} to {@code +44 7700 900999}, which Ofcom never assigns).
 */
public final class ContactPolicy {

    public static final ContactPolicy ANYONE = new ContactPolicy(false);
    public static final ContactPolicy TEST_CONTACTS_ONLY = new ContactPolicy(true);

    static final String REFUSED = "The sandbox takes only test contacts: an address at sandbox.example, "
            + "or a number from +44 7700 900000 to +44 7700 900999";

    private static final String TEST_DOMAIN = "sandbox.example";
    private static final Pattern TEST_NUMBER = Pattern.compile("^\\+447700900[0-9]{3}$");

    private final boolean testContactsOnly;

    private ContactPolicy(boolean testContactsOnly) {
        this.testContactsOnly = testContactsOnly;
    }

    public static ContactPolicy of(boolean sandbox) {
        return sandbox ? TEST_CONTACTS_ONLY : ANYONE;
    }

    /** Why {@code recipient} can't be asked here, or empty when they can. */
    public Optional<String> refusal(Recipient recipient) {
        if (!testContactsOnly) {
            return Optional.empty();
        }
        boolean test = switch (recipient.typ()) {
            case EMAIL -> {
                String domain = recipient.val().substring(recipient.val().lastIndexOf('@') + 1);
                yield domain.equals(TEST_DOMAIN) || domain.endsWith("." + TEST_DOMAIN);
            }
            case PHONE -> TEST_NUMBER.matcher(recipient.val()).matches();
        };
        return test ? Optional.empty() : Optional.of(REFUSED);
    }

    public boolean testContactsOnly() {
        return testContactsOnly;
    }
}
