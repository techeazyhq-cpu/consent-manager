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

import java.util.Locale;
import java.util.Optional;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.techeazyhq.cm.cnscmd.app.LinkDpCmd;
import com.techeazyhq.cm.cnscmd.app.LinkDpCmdHandler;
import com.techeazyhq.cm.cnscmd.dom.ExtDpRefTyp;
import com.techeazyhq.cm.seckit.AuthCtx;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Mono;

/**
 * A data principal claims the consent requests raised against their contact before they had an account (plan §7a).
 * Both the principal and the contact come from their own token, never the request body: their id from {@code sub},
 * and their contact from a claim the identity provider verified. The email address must be verified, which the
 * one-time-code sign-in does by sending the code there, and a phone number must arrive as a verified
 * {@code phone_number}. Otherwise anyone signed in could claim, read and decide someone else's requests by naming that
 * person's address in the body.
 *
 * <p>The body is optional. It names the kind of contact (EMAIL by default) and may repeat the value; a value other than
 * the token's is refused with 403, rather than quietly linking something else.
 */
public class DpLinkHandler {

    private final LinkDpCmdHandler linkDpHandler;

    public DpLinkHandler(LinkDpCmdHandler linkDpHandler) {
        this.linkDpHandler = linkDpHandler;
    }

    public Mono<ServerResponse> link(ServerRequest request) {
        return request.principal().cast(JwtAuthenticationToken.class).map(t -> (Jwt) t.getPrincipal())
                .zipWith(request.bodyToMono(DpLinkReqDto.class).defaultIfEmpty(new DpLinkReqDto(null, null)))
                .flatMap(t -> Mono.fromCallable(() -> command(t.getT1(), t.getT2())))
                .flatMap(linkDpHandler::handle)
                .flatMap(count -> ServerResponse.ok().bodyValue(new LinkResultDto(count)));
    }

    /**
     * The link the token allows.
     *
     * @throws DomRuleViolation 403 for a caller who isn't a data principal, a contact the token doesn't prove, or a
     *                          body naming another contact
     */
    static LinkDpCmd command(Jwt jwt, DpLinkReqDto body) {
        AuthCtx ctx = AuthCtx.from(jwt);
        if (!ctx.isDp()) {
            throw new DomRuleViolation(403, "not-a-data-principal", "Only a data principal claims consent requests");
        }
        ExtDpRefTyp typ = body.extDpRefTyp() == null ? ExtDpRefTyp.EMAIL : body.extDpRefTyp();
        String contact = verifiedContact(jwt, typ).orElseThrow(() -> new DomRuleViolation(403, "contact-not-verified",
                "Your sign-in doesn't verify a" + (typ == ExtDpRefTyp.EMAIL ? "n email address" : " phone number")
                        + " to claim requests with"));
        if (body.extDpRefVal() != null && !body.extDpRefVal().isBlank()
                && !normalise(typ, body.extDpRefVal()).equals(contact)) {
            throw new DomRuleViolation(403, "contact-not-yours", "You can only claim requests sent to your own "
                    + (typ == ExtDpRefTyp.EMAIL ? "email address" : "phone number"));
        }
        return new LinkDpCmd(ctx.subject(), typ, contact);
    }

    /** The token's own contact of kind {@code typ}, normalised as stored, when the identity provider verified it. */
    static Optional<String> verifiedContact(Jwt jwt, ExtDpRefTyp typ) {
        String claim = typ == ExtDpRefTyp.EMAIL ? "email" : "phone_number";
        String verified = typ == ExtDpRefTyp.EMAIL ? "email_verified" : "phone_number_verified";
        String value = jwt.getClaimAsString(claim);
        if (value == null || value.isBlank() || !Boolean.TRUE.equals(jwt.getClaimAsBoolean(verified))) {
            return Optional.empty();
        }
        return Optional.of(normalise(typ, value));
    }

    /** As {@code DpRef} stores a contact: an email address trimmed and in lower case, a phone number trimmed. */
    private static String normalise(ExtDpRefTyp typ, String raw) {
        String trimmed = raw.trim();
        return typ == ExtDpRefTyp.EMAIL ? trimmed.toLowerCase(Locale.ROOT) : trimmed;
    }

    record LinkResultDto(long linkedCount) {
    }
}
