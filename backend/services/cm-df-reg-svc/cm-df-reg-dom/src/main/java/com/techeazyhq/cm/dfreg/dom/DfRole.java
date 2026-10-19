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

import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;

/**
 * What a person at a fiduciary may do (ADR-033), held as the Keycloak realm role {@code DF_<NAME>}. API clients have
 * none of these roles: they are systems, not people.
 */
public enum DfRole {

    /** Everything, including granting or removing ownership. */
    OWNER,
    /** The team, API clients, webhooks and the organisation. */
    ADMIN,
    /** Purposes and notices, raising requests, the dashboard, relayed requests. */
    CONSENT_MANAGER,
    /** API clients and webhooks in the sandbox; read-only in production. */
    DEVELOPER,
    /** Read-only: dashboard, records, receipts, the team's activity. */
    AUDITOR;

    private static final String PREFIX = "DF_";

    public String keycloakRole() {
        return PREFIX + name();
    }

    public boolean managesTeam() {
        return this == OWNER || this == ADMIN;
    }

    /** Who may take sandbox API keys for their fiduciary (ADR-038): the people who build integrations. */
    public boolean usesSandbox() {
        return this == OWNER || this == ADMIN || this == DEVELOPER;
    }

    /** Who issues and revokes the organisation's API keys (ADR-041): Owners, Administrators and Developers. */
    public boolean managesApiKeys() {
        return this == OWNER || this == ADMIN || this == DEVELOPER;
    }

    public boolean raisesRequests() {
        return this == OWNER || this == ADMIN || this == CONSENT_MANAGER;
    }

    /** The member role among a token's or a user's realm roles, if any; the most powerful wins. */
    public static Optional<DfRole> fromRoles(Collection<String> roles) {
        return Arrays.stream(values()).filter(role -> roles.contains(role.keycloakRole())).findFirst();
    }
}
