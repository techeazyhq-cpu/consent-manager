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

package com.techeazyhq.cm.cnscmd.ifc.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import com.techeazyhq.cm.cnscmd.app.LinkDpCmd;
import com.techeazyhq.cm.cnscmd.dom.ExtDpRefTyp;
import com.techeazyhq.cm.webkit.DomRuleViolation;

/** Claiming consent requests takes the contact from the token, never the body. */
class DpLinkHandlerTest {

    private static Jwt token(String realm, List<String> roles, Map<String, Object> claims) {
        Jwt.Builder b = Jwt.withTokenValue("t").header("alg", "RS256").subject("dp-1")
                .issuer("http://localhost:8080/realms/" + realm).claim("roles", roles)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300));
        claims.forEach(b::claim);
        return b.build();
    }

    private static Jwt principal(Map<String, Object> claims) {
        return token("cm-dp", List.of("DP"), claims);
    }

    private static int refused(Jwt jwt, DpLinkReqDto body, String type) {
        DomRuleViolation e = assertThrows(DomRuleViolation.class, () -> DpLinkHandler.command(jwt, body));
        assertEquals(type, e.problemType());
        return e.httpStatus();
    }

    @Test
    void thePrincipalClaimsWhatWasSentToTheirOwnVerifiedEmail() {
        Jwt me = principal(Map.of("email", "Asha@Example.com", "email_verified", true));

        LinkDpCmd cmd = DpLinkHandler.command(me, new DpLinkReqDto(ExtDpRefTyp.EMAIL, " asha@example.COM "));
        assertEquals(new LinkDpCmd("dp-1", ExtDpRefTyp.EMAIL, "asha@example.com"), cmd);
        // the body may leave the contact out: the token's is used
        assertEquals(cmd, DpLinkHandler.command(me, new DpLinkReqDto(null, null)));
    }

    @Test
    void someoneElsesAddressIsRefused() {
        Jwt me = principal(Map.of("email", "asha@example.com", "email_verified", true));

        assertEquals(403, refused(me, new DpLinkReqDto(ExtDpRefTyp.EMAIL, "victim@example.com"), "contact-not-yours"));
    }

    @Test
    void anUnverifiedOrMissingContactIsRefused() {
        assertEquals(403, refused(principal(Map.of("email", "asha@example.com", "email_verified", false)),
                new DpLinkReqDto(null, null), "contact-not-verified"));
        assertEquals(403, refused(principal(Map.of()), new DpLinkReqDto(null, null), "contact-not-verified"));
        // a phone number is linked only when the token carries a verified one
        assertEquals(403, refused(principal(Map.of("email", "asha@example.com", "email_verified", true)),
                new DpLinkReqDto(ExtDpRefTyp.PHONE, "+919876543210"), "contact-not-verified"));
        assertEquals(new LinkDpCmd("dp-1", ExtDpRefTyp.PHONE, "+919876543210"), DpLinkHandler.command(
                principal(Map.of("phone_number", "+919876543210", "phone_number_verified", true)),
                new DpLinkReqDto(ExtDpRefTyp.PHONE, null)));
    }

    @Test
    void onlyADataPrincipalClaims() {
        Jwt dfMember = token("cm-df", List.of("DF_OWNER"), Map.of("email", "asha@example.com", "email_verified", true,
                "df_id", "df-1"));
        assertEquals(403, refused(dfMember, new DpLinkReqDto(null, null), "not-a-data-principal"));
    }
}
