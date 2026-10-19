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

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import com.techeazyhq.cm.audkit.Actor;

/** ADR-040: who the audit ledger says acted, as each realm's token says. */
class ActorOfTest {

    private static Jwt token(String realm, List<String> roles, Map<String, Object> claims) {
        Jwt.Builder b = Jwt.withTokenValue("t").header("alg", "RS256").subject("sub-1")
                .issuer("http://localhost:8080/realms/" + realm).issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60)).claim("roles", roles);
        claims.forEach(b::claim);
        return b.build();
    }

    @Test
    void staffAndMembersByEmail_systemsByClient_principalsByIdOnly() {
        assertEquals(new Actor(Actor.Kind.STAFF, "sub-1", "admin@cm.local"),
                ActorOf.token(token("cm-staff", List.of("CM_ADMIN"), Map.of("email", "admin@cm.local"))));
        assertEquals(new Actor(Actor.Kind.DF_MEMBER, "sub-1", "owner@acme.example"),
                ActorOf.token(token("cm-df", List.of("DF", "DF_OWNER"), Map.of("df_id", "df-1", "email", "owner@acme.example"))));
        assertEquals(new Actor(Actor.Kind.DF_SYSTEM, "sub-1", "df-api-df-1"),
                ActorOf.token(token("cm-df", List.of("DF"), Map.of("df_id", "df-1", "azp", "df-api-df-1"))));
        assertEquals(new Actor(Actor.Kind.DP, "sub-1", "sub-1"),
                ActorOf.token(token("cm-dp", List.of("DP"), Map.of("email", "asha@example.com"))), "never the email");
        assertEquals(Actor.SYSTEM, ActorOf.token(token("cm-realm", List.of("CM_ADMIN"), Map.of())), "untrusted realm");
    }
}
