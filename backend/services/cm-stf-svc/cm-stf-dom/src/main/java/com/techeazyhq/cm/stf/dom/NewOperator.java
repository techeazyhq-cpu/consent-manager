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

package com.techeazyhq.cm.stf.dom;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * An operator to add. They sign in like everyone else, with a one-time code sent to {@code email} (ADR-008), so an
 * email address and a name are all an account needs; there is no password to set.
 */
public record NewOperator(String email, String name) {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public NewOperator {
        if (email == null || !EMAIL.matcher(email.trim()).matches() || email.trim().length() > 255) {
            throw new IllegalArgumentException("email is not valid");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (name.trim().length() > 100) {
            throw new IllegalArgumentException("name must be at most 100 characters");
        }
        email = email.trim().toLowerCase(Locale.ROOT);
        name = name.trim();
    }
}
