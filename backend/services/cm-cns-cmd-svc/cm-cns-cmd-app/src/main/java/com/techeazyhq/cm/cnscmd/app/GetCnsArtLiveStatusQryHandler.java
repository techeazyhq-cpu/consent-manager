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

import reactor.core.publisher.Mono;

/**
 * Backs {@code GET /internal/cns-arts/{id}/status}, which the PDP asks before honouring a consent token (ADR-027). It
 * reads the write side, so a revoke takes effect on the very next check; the read model, updated through events, lags
 * a revoke and must never decide a verification. Empty for an unknown artefact, which the PDP denies.
 */
public class GetCnsArtLiveStatusQryHandler {

    private final CnsArtRepoPort cnsArtRepo;
    private final Clock clock;

    public GetCnsArtLiveStatusQryHandler(CnsArtRepoPort cnsArtRepo, Clock clock) {
        this.cnsArtRepo = cnsArtRepo;
        this.clock = clock;
    }

    public Mono<CnsArtSts> handle(String cnsArtId) {
        return cnsArtRepo.findById(cnsArtId).map(cnsArt -> cnsArt.liveStatus(clock.instant()));
    }
}
