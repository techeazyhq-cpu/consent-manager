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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/** ADR-035: each realm is believed only for its own audience. */
class RealmTrustTest {

    private static Jwt token(String realm, List<String> roles, String dfId) {
        Jwt.Builder b = Jwt.withTokenValue("t").header("alg", "RS256").subject("user-1")
                .issuer("http://localhost:8080/realms/" + realm).issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60)).claim("roles", roles);
        return dfId == null ? b.build() : b.claim("df_id", dfId).build();
    }

    @Test
    void findsTheAudienceFromTheIssuer() {
        assertEquals(Optional.of(Audience.DATA_PRINCIPAL), Audience.ofIssuer("http://localhost:8080/realms/cm-dp"));
        assertEquals(Optional.of(Audience.PLATFORM_STAFF), Audience.ofIssuer("https://id.example/realms/cm-staff/"));
        assertEquals(Optional.empty(), Audience.ofIssuer("http://localhost:8080/realms/cm-realm"));
        assertEquals(Optional.empty(), Audience.ofIssuer(null));
    }

    @Test
    void aSandboxRealmIsItsAudiencesRealmWithAPrefix() {
        assertEquals(Optional.of(Audience.FIDUCIARY), Audience.ofIssuer("http://localhost:8080/realms/sbx-cm-df"));
        assertEquals(Optional.of(Audience.DATA_PRINCIPAL), Audience.ofIssuer("http://localhost:8080/realms/sbx-cm-dp"));
        assertEquals(Optional.empty(), Audience.ofIssuer("http://localhost:8080/realms/sbxcm-df"));
        assertEquals(Optional.empty(), Audience.ofIssuer("http://localhost:8080/realms/cm-df-x"));
    }

    @Test
    void theSandboxAndProductionNeverTrustEachOthersIssuers() {
        String base = "http://localhost:8080/realms/";
        Map<String, Audience> production = RealmTokens.trustedIssuers(base, "");
        Map<String, Audience> sandbox = RealmTokens.trustedIssuers(base, "sbx-");

        assertEquals(Audience.FIDUCIARY, production.get(base + "cm-df"));
        assertEquals(Audience.FIDUCIARY, sandbox.get(base + "sbx-cm-df"));
        assertEquals(3, production.size());
        assertTrue(production.keySet().stream().noneMatch(sandbox::containsKey));
    }

    @Test
    void eachRealmKeepsOnlyItsOwnRoles() {
        AuthCtx staff = AuthCtx.from(token("cm-staff", List.of("CM_ADMIN", "DP", "DF_OWNER"), "df-1"));
        assertEquals(Set.of("CM_ADMIN"), staff.roles());
        assertNull(staff.dfId(), "only the fiduciaries' realm says which fiduciary someone belongs to");
        assertNull(staff.dpId());

        AuthCtx df = AuthCtx.from(token("cm-df", List.of("DF", "DF_OWNER", "CM_ADMIN"), "df-1"));
        assertEquals(Set.of("DF", "DF_OWNER"), df.roles());
        assertEquals("df-1", df.dfId());
        assertFalse(df.isPlatformStaff());

        AuthCtx dp = AuthCtx.from(token("cm-dp", List.of("DP", "CM_ADMIN"), "df-1"));
        assertEquals(Set.of("DP"), dp.roles());
        assertEquals("user-1", dp.dpId());
        assertNull(dp.dfId());
    }

    @Test
    void aSelfRegisteredPrincipalCanNeverBecomeStaff() {
        AuthCtx forged = AuthCtx.from(token("cm-dp", List.of("CM_ADMIN", "CM_OPERATOR"), null));
        assertFalse(forged.isCmAdmin());
        assertFalse(forged.isPlatformStaff());
        assertTrue(RealmTokens.authorities(Audience.DATA_PRINCIPAL, token("cm-dp", List.of("CM_ADMIN"), null)).isEmpty());
    }

    @Test
    void aTokenFromAnyOtherRealmCarriesNothing() {
        AuthCtx stranger = AuthCtx.from(token("cm-realm", List.of("CM_ADMIN", "DP"), "df-1"));
        assertEquals(Set.of(), stranger.roles());
        assertNull(stranger.dfId());
        assertNull(stranger.dpId());
    }

    @Test
    void hasRoleSeesTheSameFilteredRoles() {
        List<String> authorities = RealmTokens.authorities(Audience.PLATFORM_STAFF,
                token("cm-staff", List.of("CM_OPERATOR", "DF_ADMIN"), null)).stream().map(GrantedAuthority::getAuthority).toList();
        assertEquals(List.of("ROLE_CM_OPERATOR"), authorities);
    }
}
