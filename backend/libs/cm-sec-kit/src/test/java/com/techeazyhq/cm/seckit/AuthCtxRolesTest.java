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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

/** What a fiduciary's people and systems may change, by role (ADR-033). */
class AuthCtxRolesTest {

    private static AuthCtx member(String role) {
        return new AuthCtx("u-1", "df-1", null, Set.of(role));
    }

    @Test
    void webhooksAreManagedByThePeopleWhoBuildIntegrationsAndBySystems() {
        assertTrue(member("DF_OWNER").mayManageWebhooks());
        assertTrue(member("DF_ADMIN").mayManageWebhooks());
        assertTrue(member("DF_DEVELOPER").mayManageWebhooks());
        assertTrue(new AuthCtx("svc", "df-1", null, Set.of()).mayManageWebhooks(), "an API client");
        assertFalse(member("DF_CONSENT_MANAGER").mayManageWebhooks());
        assertFalse(member("DF_AUDITOR").mayManageWebhooks());
        assertFalse(new AuthCtx("dp", null, "dp-1", Set.of()).mayManageWebhooks(), "a principal");
    }

    @Test
    void requestsAreRaisedByConsentPeopleNotDevelopers() {
        assertTrue(member("DF_CONSENT_MANAGER").mayRaiseCnsReqs());
        assertFalse(member("DF_DEVELOPER").mayRaiseCnsReqs());
        assertFalse(member("DF_AUDITOR").mayManagePurposes());
    }
}
