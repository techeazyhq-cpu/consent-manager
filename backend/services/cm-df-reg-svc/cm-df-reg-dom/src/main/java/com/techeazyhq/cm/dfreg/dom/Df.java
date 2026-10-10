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

import com.techeazyhq.cm.domkit.Agg;

/** The Data Fiduciary Aggregate Root (plan §3). */
public final class Df extends Agg<String> {

    private final String name;
    private final String contactEmail;
    private final Instant registeredAt;
    private DfSts sts;
    private String cmDfApiClientSecret;
    private Instant activatedAt;

    Df(String id, String name, String contactEmail, DfSts sts, Instant registeredAt,
       String cmDfApiClientSecret, Instant activatedAt) {
        super(id);
        this.name = name;
        this.contactEmail = contactEmail;
        this.sts = sts;
        this.registeredAt = registeredAt;
        this.cmDfApiClientSecret = cmDfApiClientSecret;
        this.activatedAt = activatedAt;
    }

    public String name() {
        return name;
    }

    public String contactEmail() {
        return contactEmail;
    }

    public DfSts sts() {
        return sts;
    }

    public Instant registeredAt() {
        return registeredAt;
    }

    public String cmDfApiClientSecret() {
        return cmDfApiClientSecret;
    }

    public Instant activatedAt() {
        return activatedAt;
    }

    /** {@code clientSecret} comes from Keycloak Admin API provisioning, performed by -infra before this is called (plan §10). */
    public void activate(String clientSecret) {
        if (sts != DfSts.PENDING) {
            throw new IllegalStateException("Cannot activate a DF in status " + sts);
        }
        this.sts = DfSts.ACTIVE;
        this.cmDfApiClientSecret = clientSecret;
        this.activatedAt = Instant.now();
        raise(DfActivatedEvt.of(id(), name));
    }
}
