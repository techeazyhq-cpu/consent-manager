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

import java.time.Clock;

import com.techeazyhq.cm.cnscmd.dom.CnsArtRepoPort;
import com.techeazyhq.cm.cnscmd.dom.CnsArtSts;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Mono;

/**
 * A fiduciary exchanges a consent artefact for a fresh short-lived token (ADR-027). Only the fiduciary the artefact
 * names gets one, and only while the consent is valid right now. An artefact of another fiduciary answers exactly as a
 * missing one does (404), so the endpoint can't be used to probe for other fiduciaries' consents.
 */
public class RefreshCnsTknCmdHandler {

    public record Cmd(String cnsArtId, String callerDfId) {
    }

    private final CnsArtRepoPort cnsArtRepo;
    private final CnsTknIssuer issuer;
    private final Clock clock;

    public RefreshCnsTknCmdHandler(CnsArtRepoPort cnsArtRepo, CnsTknIssuer issuer, Clock clock) {
        this.cnsArtRepo = cnsArtRepo;
        this.issuer = issuer;
        this.clock = clock;
    }

    public Mono<CnsTknIssuer.Issued> handle(Cmd cmd) {
        return cnsArtRepo.findById(cmd.cnsArtId())
                .filter(cnsArt -> cmd.callerDfId() != null && cmd.callerDfId().equals(cnsArt.dfId()))
                .switchIfEmpty(Mono.error(new DomRuleViolation(404, "cns-art-not-found", "No CnsArt " + cmd.cnsArtId())))
                .flatMap(cnsArt -> {
                    CnsArtSts status = cnsArt.liveStatus(clock.instant());
                    if (status != CnsArtSts.ISSUED) {
                        return Mono.error(new DomRuleViolation(409, "cns-art-not-active",
                                "CnsArt " + cnsArt.id() + " is " + status));
                    }
                    return Mono.just(issuer.issue(cnsArt));
                });
    }
}
