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

package com.techeazyhq.cm.stf.app;

import com.techeazyhq.cm.stf.dom.StfAcct;
import com.techeazyhq.cm.stf.dom.StfDirectoryPort;
import com.techeazyhq.cm.stf.dom.StfRole;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Mono;

/**
 * An administrator disables an operator (signing them out everywhere at once) or enables them again. Nobody can
 * disable themselves or another administrator from here, so the platform can't be left without one.
 */
public class SetStfEnabledCmdHandler {

    public record Cmd(StfCaller caller, String stfId, boolean enabled) {
    }

    private final StfDirectoryPort directory;

    public SetStfEnabledCmdHandler(StfDirectoryPort directory) {
        this.directory = directory;
    }

    public Mono<StfAcct> handle(Cmd cmd) {
        return Mono.fromRunnable(() -> cmd.caller().requireAdmin())
                .then(Mono.defer(() -> directory.findStaff(cmd.stfId())))
                .switchIfEmpty(Mono.error(new DomRuleViolation(404, "staff-not-found", "No staff account " + cmd.stfId())))
                .flatMap(acct -> {
                    if (acct.id().equals(cmd.caller().id())) {
                        return Mono.error(new DomRuleViolation(409, "cannot-change-self",
                                "You cannot disable or enable your own account"));
                    }
                    if (acct.role() == StfRole.CM_ADMIN) {
                        return Mono.error(new DomRuleViolation(409, "cannot-change-admin",
                                "Administrators are managed in Keycloak, not here"));
                    }
                    return directory.setEnabled(acct.id(), cmd.enabled())
                            .thenReturn(new StfAcct(acct.id(), acct.email(), acct.name(), acct.role(), cmd.enabled(),
                                    acct.createdAt()));
                });
    }
}
