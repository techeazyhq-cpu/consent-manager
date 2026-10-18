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
import java.util.regex.Pattern;

/**
 * A person to add to a fiduciary's team. They sign in with a one-time code sent to {@code email} (ADR-008), so an email
 * address, a name and a role are all an account needs.
 */
public record NewDfMember(String email, String name, DfRole role) {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    /** What Keycloak's person-name validator refuses in a name. */
    private static final Pattern NOT_IN_A_NAME = Pattern.compile("[<>&\"$%!#?§;*~/\\\\|^=\\[\\]{}()\\p{Cntrl}]");

    public NewDfMember {
        if (email == null || email.trim().length() > 255 || !EMAIL.matcher(email.trim()).matches()) {
            throw new IllegalArgumentException("email is not valid");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (name.trim().length() > 100) {
            throw new IllegalArgumentException("name must be at most 100 characters");
        }
        if (NOT_IN_A_NAME.matcher(name).find()) {
            throw new IllegalArgumentException("name must not contain any of < > & \" $ % ! # ? ; * ~ / \\ | ^ = [ ] { } ( )");
        }
        if (role == null) {
            throw new IllegalArgumentException("role is required");
        }
        email = email.trim().toLowerCase(Locale.ROOT);
        name = name.trim();
    }

    /** {@code text} made into an acceptable name: the refused characters dropped, at most 100 characters. */
    public static String nameFrom(String text, String fallback) {
        String name = text == null ? "" : NOT_IN_A_NAME.matcher(text).replaceAll(" ").replaceAll("\\s+", " ").trim();
        if (name.length() > 100) {
            name = name.substring(0, 100).trim();
        }
        return name.isEmpty() ? fallback : name;
    }
}
