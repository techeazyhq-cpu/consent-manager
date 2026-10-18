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

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Who signs in where (ADR-035): each audience has its own Keycloak realm, and a realm is trusted only for its own
 * audience's roles. A token from the data principals' realm can never carry a staff or fiduciary role into a service,
 * whatever its claims say.
 */
public enum Audience {

    /** People whose data it is: the DP portal. Anyone can sign up here. */
    DATA_PRINCIPAL("cm-dp", role -> role.equals("DP")),
    /** People at fiduciaries (DF Console) and their systems (API clients), with a {@code df_id}. */
    FIDUCIARY("cm-df", role -> role.equals("DF") || role.startsWith("DF_")),
    /** The people who run the platform: CM Admin. */
    PLATFORM_STAFF("cm-staff", role -> role.equals("CM_ADMIN") || role.equals("CM_OPERATOR"));

    private static final String REALMS_PATH = "/realms/";

    private final String realm;
    private final Predicate<String> mayAssert;

    Audience(String realm, Predicate<String> mayAssert) {
        this.realm = realm;
        this.mayAssert = mayAssert;
    }

    public String realm() {
        return realm;
    }

    /** Whether a token from this audience's realm is believed when it carries {@code role}. */
    public boolean mayAssert(String role) {
        return role != null && mayAssert.test(role);
    }

    /**
     * The audience whose realm issued a token, from its {@code iss}; empty for any other issuer. A sandbox realm
     * ({@code sbx-cm-df}) is its audience's realm with a prefix; which issuers are accepted at all is pinned by
     * {@link RealmTokens}.
     */
    public static Optional<Audience> ofIssuer(String issuer) {
        if (issuer == null) {
            return Optional.empty();
        }
        int at = issuer.lastIndexOf(REALMS_PATH);
        if (at < 0) {
            return Optional.empty();
        }
        String realm = issuer.substring(at + REALMS_PATH.length()).replaceAll("/+$", "");
        return Arrays.stream(values()).filter(a -> realm.equals(a.realm) || realm.endsWith("-" + a.realm)).findFirst();
    }
}
