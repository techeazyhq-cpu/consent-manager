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

import com.techeazyhq.cm.cnscmd.dom.CnsArt;
import com.techeazyhq.cm.cnscmd.dom.CnsArtRepoPort;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Mono;

/** Stage 4: Asynchronous Revocation (plan §7c step 5). {@code CnsArt.revoke()} raises {@code CnsRevokedEvt} itself. */
public class RevokeCnsArtCmdHandler implements CmdHandler<RevokeCnsArtCmd, CnsArt> {

    private final CnsArtRepoPort cnsArtRepo;

    public RevokeCnsArtCmdHandler(CnsArtRepoPort cnsArtRepo) {
        this.cnsArtRepo = cnsArtRepo;
    }

    @Override
    @Transactional
    public Mono<CnsArt> handle(RevokeCnsArtCmd cmd) {
        return cnsArtRepo.findById(cmd.cnsArtId())
                // someone else's consent is reported as not found, so its existence isn't given away
                .filter(cnsArt -> cnsArt.dpRef().isOf(cmd.dpId()))
                .switchIfEmpty(Mono.error(new DomRuleViolation(404, "cns-art-not-found", "No CnsArt " + cmd.cnsArtId())))
                .flatMap(cnsArt -> {
                    try {
                        cnsArt.revoke();
                    } catch (IllegalStateException e) {
                        return Mono.error(new DomRuleViolation(409, "cns-art-not-issued", e.getMessage()));
                    }
                    return cnsArtRepo.save(cnsArt);
                });
    }
}
