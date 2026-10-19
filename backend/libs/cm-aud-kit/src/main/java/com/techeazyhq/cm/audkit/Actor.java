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

package com.techeazyhq.cm.audkit;

import java.util.Objects;

/**
 * Who did something the audit ledger records (ADR-040), as their token said. {@code id} is the token's subject;
 * {@code name} is what people read: an email address for people, the client id for systems.
 */
public record Actor(Kind kind, String id, String name) {

    public enum Kind {
        /** Platform administrators and operators (cm-staff). */
        STAFF,
        /** A person at a fiduciary (cm-df, with a DF_ role). */
        DF_MEMBER,
        /** A fiduciary's system: an API client (cm-df, no person role). */
        DF_SYSTEM,
        /** A data principal (cm-dp). */
        DP,
        /** The platform itself: a scheduled job, a reaction to another event. */
        SYSTEM
    }

    /** No one signed in: the platform acting on its own. */
    public static final Actor SYSTEM = new Actor(Kind.SYSTEM, "system", "Consent Manager");

    public Actor {
        Objects.requireNonNull(kind, "kind");
        id = id == null ? "" : id;
        name = name == null || name.isBlank() ? id : name;
    }
}
