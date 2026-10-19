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

import java.time.Instant;
import java.util.Optional;

import org.springframework.transaction.annotation.Transactional;

import com.techeazyhq.cm.cnscmd.dom.CnsArt;
import com.techeazyhq.cm.cnscmd.dom.CnsArtFactory;
import com.techeazyhq.cm.cnscmd.dom.CnsArtRepoPort;
import com.techeazyhq.cm.cnscmd.dom.CnsGrant;
import com.techeazyhq.cm.cnscmd.dom.CnsGrantedEvt;
import com.techeazyhq.cm.cnscmd.dom.CnsReq;
import com.techeazyhq.cm.cnscmd.dom.CnsReqDecidablePolicy;
import com.techeazyhq.cm.cnscmd.dom.CnsReqRepoPort;
import com.techeazyhq.cm.cnscmd.dom.ObxPort;
import com.techeazyhq.cm.cnscmd.dom.PrpsVerContent;
import com.techeazyhq.cm.domkit.Spec;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Mono;

/**
 * Stage 2 decided: approve (plan §7c step 3), by the principal the request names and no one else. The consent
 * covers the categories they chose for the validity they chose (ADR-026). Creates the {@link CnsArt}, signs its
 * {@code CnsTkn}, and explicitly enqueues the composed {@code CnsGrantedEvt} via
 * {@link ObxPort} since it needs data from both aggregates plus the signature — it isn't
 * one aggregate's own {@code pullEvts()} event.
 */
public class ApproveCnsCmdHandler implements CmdHandler<ApproveCnsReqCmd, CnsArt> {

    private final CnsReqRepoPort cnsReqRepo;
    private final CnsArtRepoPort cnsArtRepo;
    private final ObxPort obxPort;
    private final CnsTknIssuer cnsTknIssuer;
    private final CnsReqTerms terms;
    private final CnsReqDecidablePolicy decidablePolicy = new CnsReqDecidablePolicy();

    public ApproveCnsCmdHandler(CnsReqRepoPort cnsReqRepo, CnsArtRepoPort cnsArtRepo, ObxPort obxPort,
                                 CnsTknIssuer cnsTknIssuer, CnsReqTerms terms) {
        this.cnsReqRepo = cnsReqRepo;
        this.cnsArtRepo = cnsArtRepo;
        this.obxPort = obxPort;
        this.cnsTknIssuer = cnsTknIssuer;
        this.terms = terms;
    }

    @Override
    @Transactional
    public Mono<CnsArt> handle(ApproveCnsReqCmd cmd) {
        return CnsReqTerms.ownRequest(cnsReqRepo, cmd.cnsReqId(), cmd.dpId())
                .flatMap(this::requireDecidable)
                .flatMap(cnsReq -> terms.versionOf(cnsReq).map(Optional::of)
                        .defaultIfEmpty(Optional.empty())
                        .map(version -> grantOf(cnsReq, version.orElse(null), cmd)))
                .flatMap(decided -> {
                    CnsReq cnsReq = decided.req();
                    CnsGrant grant = decided.grant();
                    cnsReq.approve();
                    CnsArt cnsArt = CnsArtFactory.issue(
                            cnsReq.id(), cnsReq.dfId(), cnsReq.dpRef(), cnsReq.prps(), grant.scp(), grant.valPrd());

                    // A short-lived token (ADR-027): the fiduciary refreshes it while the consent is valid.
                    String cnsTkn = cnsTknIssuer.issue(cnsArt).token();
                    cnsArt.attachSignedTkn(cnsTkn);

                    return cnsReqRepo.save(cnsReq)
                            .then(cnsArtRepo.save(cnsArt))
                            .flatMap(savedArt -> obxPort
                                    .enqueue(CnsGrantedEvt.of(cnsReq.id(), savedArt.id(), cnsReq.dfId(), cnsReq.dpRef(),
                                                    cnsTkn, grant),
                                            "CnsArt", savedArt.id())
                                    .thenReturn(savedArt));
                });
    }

    private record Decided(CnsReq req, CnsGrant grant) {
    }

    /** The principal's choice, checked against what was asked and the purpose's validity (ADR-026). */
    private static Decided grantOf(CnsReq cnsReq, PrpsVerContent version, ApproveCnsReqCmd cmd) {
        try {
            return new Decided(cnsReq,
                    CnsGrant.of(cnsReq.scp(), version, cmd.dCatCodes(), cmd.validityDays(), Instant.now()));
        } catch (IllegalArgumentException e) {
            throw new DomRuleViolation(400, "invalid-grant", e.getMessage());
        }
    }

    private Mono<CnsReq> requireDecidable(CnsReq cnsReq) {
        Spec.SpecResult result = decidablePolicy.evaluate(cnsReq);
        if (!result.satisfied()) {
            return Mono.error(new DomRuleViolation(409, result.reasonCode(), "CnsReq " + cnsReq.id() + " cannot be decided"));
        }
        return Mono.just(cnsReq);
    }
}
