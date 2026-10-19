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

package com.techeazyhq.cm.seckit;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Adapter (plan §12) from a Keycloak-issued {@link Jwt} to the platform's own notion of "who is
 * calling" — isolates every service's application/interface layers from Spring Security's JWT
 * representation. {@code dfId} is populated for both interactive DF App users (PKCE) and DF
 * backend systems (client-credentials, the {@code cm-df-api} client, plan §10) since both carry
 * the same {@code df_id} claim.
 */
public record AuthCtx(String subject, String dfId, String dpId, Set<String> roles) {

    private static final String CLAIM_DF_ID = "df_id";
    private static final String CLAIM_ROLES = "roles";
    private static final String ROLE_DP = "DP";

    /**
     * {@code df_id} is a distinct claim since a DF's employees are separate Keycloak accounts
     * sharing one organizational identity. A DP has no such distinction — their CM identity
     * *is* their own Keycloak account, so {@code dpId} is simply their {@code sub} once linked
     * (plan §7a), not a separate custom claim.
     */
    public static AuthCtx from(Jwt jwt) {
        // Each realm is believed only for its own audience (ADR-035): staff roles from cm-staff, fiduciary roles and
        // df_id from cm-df, DP from cm-dp. Anything else in the token is dropped, never honoured.
        Audience audience = Audience.ofIssuer(jwt.getIssuer() == null ? null : jwt.getIssuer().toString()).orElse(null);
        List<String> roleClaim = jwt.getClaimAsStringList(CLAIM_ROLES);
        Set<String> roles = audience == null || roleClaim == null ? Set.of()
                : roleClaim.stream().filter(audience::mayAssert).collect(Collectors.toUnmodifiableSet());
        return new AuthCtx(
                jwt.getSubject(),
                audience == Audience.FIDUCIARY ? jwt.getClaimAsString(CLAIM_DF_ID) : null,
                audience == Audience.DATA_PRINCIPAL && roles.contains(ROLE_DP) ? jwt.getSubject() : null,
                roles);
    }

    public boolean isCmAdmin() {
        return roles.contains("CM_ADMIN");
    }

    /** Platform administrators and operators: the people who run the Consent Manager, not its tenants or users. */
    public boolean isPlatformStaff() {
        return isCmAdmin() || roles.contains("CM_OPERATOR");
    }

    public boolean isDf() {
        return dfId != null;
    }

    /**
     * Whether this fiduciary caller may raise consent requests (ADR-033): a system with an API client (no member
     * role), or a person whose role is Owner, Administrator or Consent manager; not a Developer or an Auditor.
     */
    public boolean mayRaiseCnsReqs() {
        if (!isDf()) {
            return false;
        }
        boolean person = roles.stream().anyMatch(role -> role.startsWith("DF_"));
        return !person || roles.contains("DF_OWNER") || roles.contains("DF_ADMIN") || roles.contains("DF_CONSENT_MANAGER");
    }

    /**
     * Whether this fiduciary caller may define and publish purposes (ADR-031, ADR-033): the same people and systems
     * that raise requests, since a purpose is what a request asks for.
     */
    public boolean mayManagePurposes() {
        return mayRaiseCnsReqs();
    }

    public boolean isDp() {
        return dpId != null;
    }
}
