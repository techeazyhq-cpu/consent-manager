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

import java.util.Optional;

import com.techeazyhq.cm.dfreg.dom.Df;
import com.techeazyhq.cm.dfreg.dom.DfApiClientProvisionPort;
import com.techeazyhq.cm.dfreg.dom.DfFactory;
import com.techeazyhq.cm.dfreg.dom.DfRepoPort;
import com.techeazyhq.cm.dfreg.dom.DfRole;
import com.techeazyhq.cm.dfreg.dom.DfSts;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Mono;

/**
 * Sandbox API keys for a production fiduciary's people (ADR-038), in the sandbox only. The first time, the sandbox
 * makes a twin of the fiduciary (same id, its name, nothing else) and activates it, which creates its API client in
 * the sandbox's realm; every time after, the client gets a new secret and the old one stops working. The secret is
 * shown once, to the person who asked.
 */
public class IssueSandboxKeysHandler {

    private final DfRepoPort dfRepo;
    private final ActivateDfCmdHandler activate;
    private final DfApiClientProvisionPort clients;

    public IssueSandboxKeysHandler(DfRepoPort dfRepo, ActivateDfCmdHandler activate, DfApiClientProvisionPort clients) {
        this.dfRepo = dfRepo;
        this.activate = activate;
        this.clients = clients;
    }

    /** A person at a production fiduciary, as their production token says: which fiduciary, their role there. */
    public record Cmd(String productionDfId, Optional<DfRole> role, String dfName) {
    }

    /** {@code created}: the fiduciary's first sandbox keys. */
    public record Keys(String dfId, String clientId, String clientSecret, boolean created) {
    }

    public Mono<Keys> handle(Cmd cmd) {
        if (cmd.productionDfId() == null) {
            return Mono.error(new DomRuleViolation(403, "not-a-fiduciary-member",
                    "Sandbox keys are for people at a fiduciary, signed in to the DF Console"));
        }
        if (cmd.role().filter(DfRole::usesSandbox).isEmpty()) {
            return Mono.error(new DomRuleViolation(403, "role-cannot-use-sandbox",
                    "Only an Owner, an Administrator or a Developer can take sandbox keys"));
        }
        Df twin;
        try {
            twin = DfFactory.sandboxTwin(cmd.productionDfId(), cmd.dfName());
        } catch (IllegalArgumentException e) {
            return Mono.error(new DomRuleViolation(400, "invalid-df-id", e.getMessage()));
        }
        return dfRepo.findById(twin.id())
                .flatMap(existing -> existing.sts() == DfSts.ACTIVE ? rotate(existing) : activated(existing))
                .switchIfEmpty(Mono.defer(() -> dfRepo.save(twin).flatMap(this::activated)));
    }

    private Mono<Keys> activated(Df pending) {
        return activate.handle(new ActivateDfCmd(pending.id()))
                .map(df -> new Keys(df.id(), clientId(df), df.cmDfApiClientSecret(), true));
    }

    private Mono<Keys> rotate(Df df) {
        return clients.rotateClientSecret(df.id())
                .doOnNext(df::replaceClientSecret)
                .then(Mono.defer(() -> dfRepo.save(df)))
                .map(saved -> new Keys(saved.id(), clientId(saved), saved.cmDfApiClientSecret(), false));
    }

    private static String clientId(Df df) {
        return "df-api-" + df.id();
    }
}
