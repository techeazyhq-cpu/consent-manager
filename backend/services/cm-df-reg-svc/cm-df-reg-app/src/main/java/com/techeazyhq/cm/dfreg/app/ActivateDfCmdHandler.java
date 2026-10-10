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

import com.techeazyhq.cm.dfreg.dom.Df;
import com.techeazyhq.cm.dfreg.dom.DfApiClientProvisionPort;
import com.techeazyhq.cm.dfreg.dom.DfRepoPort;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Mono;

/** Activation provisions the {@code cm-df-api} client secret before flipping the aggregate to ACTIVE (plan §10). */
public class ActivateDfCmdHandler implements CmdHandler<ActivateDfCmd, Df> {

    private final DfRepoPort dfRepo;
    private final DfApiClientProvisionPort provisionPort;

    public ActivateDfCmdHandler(DfRepoPort dfRepo, DfApiClientProvisionPort provisionPort) {
        this.dfRepo = dfRepo;
        this.provisionPort = provisionPort;
    }

    @Override
    @Transactional
    public Mono<Df> handle(ActivateDfCmd cmd) {
        return dfRepo.findById(cmd.dfId())
                .switchIfEmpty(Mono.error(new DomRuleViolation(404, "df-not-found", "No DF " + cmd.dfId())))
                .flatMap(df -> provisionPort.provisionClientSecret(df.id())
                        .doOnNext(df::activate)
                        // Mono.defer, not a bare dfRepo.save(df) call: the adapter reads df's
                        // fields (sts, cmDfApiClientSecret) to build the SQL bind values the
                        // moment save() is *invoked*, not when the returned Mono is
                        // *subscribed* — without defer, that invocation happens while
                        // constructing this flatMap's return value, before doOnNext(df::activate)
                        // has actually run, so it silently persisted the PENDING/no-secret
                        // state. Found via Postgres inspection during Phase 1 verification:
                        // the REST response showed ACTIVE, but the DB row stayed PENDING.
                        .then(Mono.defer(() -> dfRepo.save(df))));
    }
}
