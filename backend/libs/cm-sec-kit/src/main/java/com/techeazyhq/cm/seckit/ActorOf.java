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

import org.springframework.security.oauth2.jwt.Jwt;

import com.techeazyhq.cm.audkit.Actor;

/**
 * Who a token says is acting, for the audit ledger (ADR-040), believed per realm as {@link AuthCtx} is: platform staff,
 * a fiduciary's person or system, or a data principal. A principal is named by their id only, never their email, so
 * the ledger's actor holds no more of their personal data than the consent records do.
 */
public final class ActorOf {

    private ActorOf() {
    }

    public static Actor token(Jwt jwt) {
        AuthCtx ctx = AuthCtx.from(jwt);
        String subject = jwt.getSubject();
        String email = jwt.getClaimAsString("email");
        if (ctx.isPlatformStaff()) {
            return new Actor(Actor.Kind.STAFF, subject, email);
        }
        if (ctx.isDf()) {
            boolean person = ctx.roles().stream().anyMatch(role -> role.startsWith("DF_"));
            if (person) {
                return new Actor(Actor.Kind.DF_MEMBER, subject, email);
            }
            String client = jwt.getClaimAsString("azp");
            return new Actor(Actor.Kind.DF_SYSTEM, subject, client != null ? client : jwt.getClaimAsString("client_id"));
        }
        if (ctx.isDp()) {
            return new Actor(Actor.Kind.DP, subject, subject);
        }
        return Actor.SYSTEM;
    }
}
