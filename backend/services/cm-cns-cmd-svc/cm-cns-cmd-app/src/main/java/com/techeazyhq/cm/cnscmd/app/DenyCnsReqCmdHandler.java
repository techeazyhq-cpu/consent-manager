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

package com.techeazyhq.cm.cnscmd.app;

import org.springframework.transaction.annotation.Transactional;

import com.techeazyhq.cm.cnscmd.dom.CnsReq;
import com.techeazyhq.cm.cnscmd.dom.CnsReqDecidablePolicy;
import com.techeazyhq.cm.cnscmd.dom.CnsReqRepoPort;
import com.techeazyhq.cm.domkit.Spec;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Mono;

/** Stage 2 decided: deny (plan §7c step 3). {@code CnsReq.deny()} raises {@code CnsDeniedEvt} itself. */
public class DenyCnsReqCmdHandler implements CmdHandler<DenyCnsReqCmd, CnsReq> {

    private final CnsReqRepoPort cnsReqRepo;
    private final CnsReqDecidablePolicy decidablePolicy = new CnsReqDecidablePolicy();

    public DenyCnsReqCmdHandler(CnsReqRepoPort cnsReqRepo) {
        this.cnsReqRepo = cnsReqRepo;
    }

    @Override
    @Transactional
    public Mono<CnsReq> handle(DenyCnsReqCmd cmd) {
        return cnsReqRepo.findById(cmd.cnsReqId())
                .switchIfEmpty(Mono.error(new DomRuleViolation(404, "cns-req-not-found", "No CnsReq " + cmd.cnsReqId())))
                .flatMap(cnsReq -> {
                    Spec.SpecResult result = decidablePolicy.evaluate(cnsReq);
                    if (!result.satisfied()) {
                        return Mono.error(new DomRuleViolation(409, result.reasonCode(), "CnsReq " + cnsReq.id() + " cannot be decided"));
                    }
                    cnsReq.deny();
                    return cnsReqRepo.save(cnsReq);
                });
    }
}
