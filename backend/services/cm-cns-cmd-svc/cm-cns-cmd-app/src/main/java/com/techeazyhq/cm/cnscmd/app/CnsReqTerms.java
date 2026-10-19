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

import com.techeazyhq.cm.cnscmd.dom.CnsReq;
import com.techeazyhq.cm.cnscmd.dom.CnsReqRepoPort;
import com.techeazyhq.cm.cnscmd.dom.PrpsDefRepoPort;
import com.techeazyhq.cm.cnscmd.dom.PrpsVerContent;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Mono;

/**
 * The terms a request was made on: the purpose version it names (ADR-031), which the decision screen shows and the
 * grant is checked against (ADR-026). Only the principal the request names, and the fiduciary that raised it, may
 * read them; anyone else is told it doesn't exist.
 */
public class CnsReqTerms {

    private final CnsReqRepoPort cnsReqRepo;
    private final PrpsDefRepoPort prpsDefs;

    public CnsReqTerms(CnsReqRepoPort cnsReqRepo, PrpsDefRepoPort prpsDefs) {
        this.cnsReqRepo = cnsReqRepo;
        this.prpsDefs = prpsDefs;
    }

    /** A request and its purpose version (null only for a request stored without one). */
    public record Terms(CnsReq req, PrpsVerContent version) {
    }

    /** The request's terms, for the principal it names ({@code dpId}) or the fiduciary that raised it ({@code dfId}). */
    public Mono<Terms> forViewer(String cnsReqId, String dpId, String dfId) {
        return cnsReqRepo.findById(cnsReqId)
                .filter(req -> req.dpRef().isOf(dpId) || (dfId != null && dfId.equals(req.dfId())))
                .switchIfEmpty(Mono.error(() -> notFound(cnsReqId)))
                .flatMap(req -> versionOf(req).map(v -> new Terms(req, v)).defaultIfEmpty(new Terms(req, null)));
    }

    /** The purpose version the request was made from; empty for a request stored without one. */
    public Mono<PrpsVerContent> versionOf(CnsReq req) {
        if (req.prpsVer() == null) {
            return Mono.empty();
        }
        return prpsDefs.findByCode(req.dfId(), req.prps().code())
                .flatMap(def -> Mono.justOrEmpty(def.version(req.prpsVer().verNo())))
                .map(ver -> ver.content());
    }

    /**
     * The request, if principal {@code dpId} is the one it names: only they give, decline or withdraw consent. A
     * fiduciary, or another principal, gets 404 so the request's existence isn't given away.
     */
    static Mono<CnsReq> ownRequest(CnsReqRepoPort repo, String cnsReqId, String dpId) {
        return repo.findById(cnsReqId)
                .filter(req -> req.dpRef().isOf(dpId))
                .switchIfEmpty(Mono.error(() -> notFound(cnsReqId)));
    }

    private static DomRuleViolation notFound(String cnsReqId) {
        return new DomRuleViolation(404, "cns-req-not-found", "No CnsReq " + cnsReqId);
    }
}
