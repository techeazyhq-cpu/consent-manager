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

import com.techeazyhq.cm.audkit.AuditTrail;
import com.techeazyhq.cm.stf.dom.NewOperator;
import com.techeazyhq.cm.stf.dom.StfAcct;
import com.techeazyhq.cm.stf.dom.StfAcctExistsException;
import com.techeazyhq.cm.stf.dom.StfDirectoryPort;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Mono;

/**
 * An administrator adds an operator. Refused with 403 {@code admin-only} for anyone else, 400
 * {@code invalid-operator} for a bad email or name, and 409 {@code account-exists} when the address already has an
 * account of any kind, so a data principal can never be turned into staff by accident.
 */
public class CreateOperatorCmdHandler {

    public record Cmd(StfCaller caller, String email, String name) {
    }

    private final StfDirectoryPort directory;
    private final AuditTrail audit;

    public CreateOperatorCmdHandler(StfDirectoryPort directory) {
        this(directory, AuditTrail.NONE);
    }

    public CreateOperatorCmdHandler(StfDirectoryPort directory, AuditTrail audit) {
        this.directory = directory;
        this.audit = audit;
    }

    public Mono<StfAcct> handle(Cmd cmd) {
        return Mono.fromCallable(() -> {
                    cmd.caller().requireAdmin();
                    return new NewOperator(cmd.email(), cmd.name());
                })
                .onErrorMap(IllegalArgumentException.class,
                        e -> new DomRuleViolation(400, "invalid-operator", e.getMessage()))
                .flatMap(directory::createOperator)
                .flatMap(acct -> audit.record("staff.operator-added", null, acct.id(),
                        AuditTrail.details("staff", acct.email())).thenReturn(acct))
                .onErrorMap(StfAcctExistsException.class,
                        e -> new DomRuleViolation(409, "account-exists", e.getMessage()));
    }
}
