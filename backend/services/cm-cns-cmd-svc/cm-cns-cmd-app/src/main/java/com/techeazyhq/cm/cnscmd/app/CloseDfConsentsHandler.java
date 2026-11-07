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

import java.util.Map;

import com.techeazyhq.cm.cnscmd.dom.CnsArtRepoPort;
import com.techeazyhq.cm.cnscmd.dom.CnsReqRepoPort;
import com.techeazyhq.cm.cnscmd.dom.DpContactErasurePort;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * The Consent context's step of a fiduciary closing (ADR-064): every consent it holds in force is ended and every request
 * still waiting is declined, each through the domain so that the principal gets their record and receipt and the
 * fiduciary's webhooks fire as they do for any change. When nothing is left, the contacts the fiduciary sent requests to
 * are erased. The records themselves stay, under the pseudonymous id.
 *
 * <p>Done in batches of {@code limit}, so a fiduciary with many consents does not hold one request open: the caller asks
 * again until {@code done}. Idempotent: run again, there is nothing left to end.
 */
public class CloseDfConsentsHandler {

    private final CnsReqRepoPort reqs;
    private final CnsArtRepoPort arts;
    private final DpContactErasurePort contacts;

    public CloseDfConsentsHandler(CnsReqRepoPort reqs, CnsArtRepoPort arts, DpContactErasurePort contacts) {
        this.reqs = reqs;
        this.arts = arts;
        this.contacts = contacts;
    }

    public Mono<Map<String, Object>> handle(String dfId, int limit) {
        int batch = Math.max(1, Math.min(limit, 1000));
        return arts.findIssuedByDfId(dfId, batch).collectList().flatMapMany(Flux::fromIterable)
                .concatMap(art -> {
                    art.revoke();
                    return arts.save(art);
                })
                .count()
                .flatMap(revoked -> reqs.findPendingByDfId(dfId, batch).collectList().flatMapMany(Flux::fromIterable)
                        .concatMap(req -> {
                            req.deny();
                            return reqs.save(req);
                        })
                        .count()
                        .flatMap(declined -> {
                            boolean done = revoked < batch && declined < batch;
                            Mono<Long> erased = done ? contacts.eraseContactsOfDf(dfId) : Mono.just(0L);
                            return erased.map(n -> Map.<String, Object>of("revoked", revoked, "declined", declined,
                                    "done", done, "contactsErased", n));
                        }));
    }
}
