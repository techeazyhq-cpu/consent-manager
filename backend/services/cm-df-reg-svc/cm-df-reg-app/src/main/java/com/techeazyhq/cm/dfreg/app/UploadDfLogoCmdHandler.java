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

package com.techeazyhq.cm.dfreg.app;

import com.techeazyhq.cm.audkit.AuditTrail;
import com.techeazyhq.cm.dfreg.dom.DfLogo;
import com.techeazyhq.cm.dfreg.dom.DfLogoRepoPort;
import com.techeazyhq.cm.dfreg.dom.DfRepoPort;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Mono;

/** Sets or replaces a fiduciary's logo: 404 {@code df-not-found}, or 400 {@code invalid-logo} for a file it refuses. */
public class UploadDfLogoCmdHandler implements CmdHandler<UploadDfLogoCmd, DfLogo> {

    private final DfRepoPort dfRepo;
    private final DfLogoRepoPort logoRepo;
    private final AuditTrail audit;

    public UploadDfLogoCmdHandler(DfRepoPort dfRepo, DfLogoRepoPort logoRepo) {
        this(dfRepo, logoRepo, AuditTrail.NONE);
    }

    public UploadDfLogoCmdHandler(DfRepoPort dfRepo, DfLogoRepoPort logoRepo, AuditTrail audit) {
        this.dfRepo = dfRepo;
        this.logoRepo = logoRepo;
        this.audit = audit;
    }

    @Override
    public Mono<DfLogo> handle(UploadDfLogoCmd cmd) {
        return dfRepo.findById(cmd.dfId())
                .switchIfEmpty(Mono.error(new DomRuleViolation(404, "df-not-found", "No DF " + cmd.dfId())))
                .map(df -> DfLogo.of(df.id(), cmd.contentType(), cmd.bytes()))
                .onErrorMap(IllegalArgumentException.class, e -> new DomRuleViolation(400, "invalid-logo", e.getMessage()))
                .flatMap(logoRepo::save)
                .flatMap(logo -> audit.record("tenant.logo-changed", cmd.dfId(), cmd.dfId(),
                        AuditTrail.details("contentType", cmd.contentType())).thenReturn(logo));
    }
}
