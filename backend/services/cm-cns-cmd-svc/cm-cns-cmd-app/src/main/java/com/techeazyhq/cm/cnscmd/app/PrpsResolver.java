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

import java.util.Collection;

import com.techeazyhq.cm.cnscmd.dom.CnsReqTemplate;
import com.techeazyhq.cm.cnscmd.dom.PrpsDefRepoPort;
import com.techeazyhq.cm.cnscmd.dom.PrpsVer;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Mono;

/**
 * Turns what a request names (a purpose's code, its version, a notice language, the data categories) into what it
 * asks (ADR-031). Only the current version may be asked: an older one has been superseded, and a draft hasn't been
 * published. Each refusal says which, so a fiduciary's system can tell a stale integration from a typo.
 */
public class PrpsResolver {

    private final PrpsDefRepoPort prpsDefs;

    public PrpsResolver(PrpsDefRepoPort prpsDefs) {
        this.prpsDefs = prpsDefs;
    }

    public Mono<CnsReqTemplate> resolve(String dfId, String prpsCode, Integer prpsVer, String ntcLang,
                                        Collection<String> dCatCodes) {
        if (prpsCode == null || prpsCode.isBlank()) {
            return Mono.error(new DomRuleViolation(400, "invalid-request", "Name the purpose: prpsCode"));
        }
        if (prpsVer == null) {
            return Mono.error(new DomRuleViolation(400, "invalid-request",
                    "Name the purpose version: prpsVer. A request asks consent for one published version."));
        }
        return prpsDefs.findByCode(dfId, prpsCode.strip())
                .switchIfEmpty(Mono.error(() -> new DomRuleViolation(404, "purpose-not-found",
                        "There is no purpose " + prpsCode.strip() + ". Define and publish it first.")))
                .flatMap(def -> {
                    PrpsVer current = def.current().orElse(null);
                    if (current == null) {
                        return Mono.error(new DomRuleViolation(409, "purpose-not-published",
                                def.code() + " has no published version yet"));
                    }
                    if (prpsVer < current.verNo() && def.version(prpsVer).isPresent()) {
                        return Mono.error(new DomRuleViolation(409, "purpose-version-superseded",
                                def.code() + " version " + prpsVer + " has been superseded by version "
                                        + current.verNo() + ". Ask for the current version."));
                    }
                    if (prpsVer != current.verNo()) {
                        return Mono.error(new DomRuleViolation(404, "purpose-version-not-found",
                                def.code() + " has no published version " + prpsVer + "; the current one is "
                                        + current.verNo()));
                    }
                    try {
                        return Mono.just(def.templateFor(prpsVer, ntcLang, dCatCodes));
                    } catch (IllegalArgumentException e) {
                        return Mono.error(new DomRuleViolation(400, "invalid-request", e.getMessage()));
                    }
                });
    }
}
