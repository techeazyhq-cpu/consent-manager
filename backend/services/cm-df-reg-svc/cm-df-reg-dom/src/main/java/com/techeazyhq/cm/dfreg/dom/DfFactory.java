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

import java.time.Instant;
import java.util.UUID;

public final class DfFactory {

    private DfFactory() {
    }

    /**
     * @param name       the display name data principals see
     * @param prfl       the rest of the fiduciary's profile, validated as it was built
     * @param subPlnName the plan to subscribe the fiduciary to on activation, null or blank for FREE; checked
     *                   against cm-bil-svc's catalogue by the caller, which knows it
     */
    public static Df register(String name, String contactEmail, DfPrfl prfl, String subPlnName) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (name.trim().length() > 255) {
            throw new IllegalArgumentException("name must be at most 255 characters");
        }
        String email = DfPrfl.email(contactEmail, "contactEmail");
        String plan = subPlnName == null || subPlnName.isBlank() ? null : subPlnName.trim();
        return new Df(UUID.randomUUID().toString(), name.trim(), email, prfl, plan, DfSts.PENDING, Instant.now(),
                null, null);
    }

    public static Df rehydrate(String id, String name, String contactEmail, DfPrfl prfl, String subPlnName,
                                DfSts sts, Instant registeredAt, String cmDfApiClientSecret, Instant activatedAt) {
        return new Df(id, name, contactEmail, prfl, subPlnName, sts, registeredAt, cmDfApiClientSecret, activatedAt);
    }
}
