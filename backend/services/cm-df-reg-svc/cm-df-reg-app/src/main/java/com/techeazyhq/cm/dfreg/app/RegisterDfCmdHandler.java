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

import org.springframework.transaction.annotation.Transactional;

import com.techeazyhq.cm.audkit.AuditTrail;
import com.techeazyhq.cm.dfreg.dom.Df;
import com.techeazyhq.cm.dfreg.dom.DfFactory;
import com.techeazyhq.cm.dfreg.dom.DfRepoPort;
import com.techeazyhq.cm.dfreg.dom.SubPlnCatalogPort;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Mono;

/**
 * Onboards a Data Fiduciary as PENDING, with its profile and the plan it will be subscribed to on activation. An
 * invalid profile (400 {@code invalid-df-profile}) or a plan cm-bil-svc doesn't offer (400 {@code unknown-sub-pln})
 * is refused before anything is written.
 */
public class RegisterDfCmdHandler implements CmdHandler<RegisterDfCmd, Df> {

    private final DfRepoPort dfRepo;
    private final SubPlnCatalogPort subPlnCatalog;
    private final AuditTrail audit;

    public RegisterDfCmdHandler(DfRepoPort dfRepo, SubPlnCatalogPort subPlnCatalog) {
        this(dfRepo, subPlnCatalog, AuditTrail.NONE);
    }

    public RegisterDfCmdHandler(DfRepoPort dfRepo, SubPlnCatalogPort subPlnCatalog, AuditTrail audit) {
        this.dfRepo = dfRepo;
        this.subPlnCatalog = subPlnCatalog;
        this.audit = audit;
    }

    @Override
    @Transactional
    public Mono<Df> handle(RegisterDfCmd cmd) {
        RegisterDfCmd.PrflFields prfl = cmd.prfl() == null ? RegisterDfCmd.PrflFields.NONE : cmd.prfl();
        return Mono.fromCallable(() -> DfFactory.register(cmd.name(), cmd.contactEmail(), prfl.toPrfl(), cmd.subPlnName()))
                .onErrorMap(IllegalArgumentException.class,
                        e -> new DomRuleViolation(400, "invalid-df-profile", e.getMessage()))
                // Mono.defer: save() starts its work when called, not when subscribed, so calling it directly
                // here would run it before the plan check had a chance to refuse the registration.
                .flatMap(df -> requireKnownPlan(df).then(Mono.defer(() -> dfRepo.save(df))))
                .flatMap(df -> audit.record("tenant.registered", df.id(), df.id(), AuditTrail.details("name", df.name(),
                        "plan", df.subPlnName() == null ? "FREE" : df.subPlnName())).thenReturn(df));
    }

    private Mono<Void> requireKnownPlan(Df df) {
        if (df.subPlnName() == null) {
            return Mono.empty();
        }
        return subPlnCatalog.exists(df.subPlnName())
                .flatMap(exists -> exists
                        ? Mono.<Void>empty()
                        : Mono.error(new DomRuleViolation(400, "unknown-sub-pln",
                                "There is no subscription plan named " + df.subPlnName())));
    }
}
