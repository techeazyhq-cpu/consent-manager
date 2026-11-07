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

import java.util.ArrayList;
import java.util.Map;

import com.techeazyhq.cm.cnscmd.dom.CnsArt;
import com.techeazyhq.cm.cnscmd.dom.CnsArtRepoPort;
import com.techeazyhq.cm.cnscmd.dom.CnsArtSts;
import com.techeazyhq.cm.cnscmd.dom.CnsReq;
import com.techeazyhq.cm.cnscmd.dom.CnsReqRepoPort;
import com.techeazyhq.cm.cnscmd.dom.CnsReqSts;
import com.techeazyhq.cm.cnscmd.dom.DpContactErasurePort;
import com.techeazyhq.cm.cnscmd.dom.ExtDpRefTyp;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * The Consent context's step of a principal closing their account (ADR-059):
 * <ol>
 * <li>requests sent to their email address but not yet claimed are linked to them, so they are covered too;</li>
 * <li>every consent they gave is withdrawn and every request waiting for them declined, as if they had done it, so
 * each fiduciary is told by its webhooks and each change gets its record and receipt;</li>
 * <li>the contacts still held for them are erased ({@link DpContactErasurePort}).</li>
 * </ol>
 * The records stay, under the pseudonymous id, for the legal hold. Idempotent: run again, there is nothing left to
 * withdraw or erase. Answers with every consent request of theirs, for the audit step, and how many it withdrew.
 */
public class EraseDpConsentsHandler {

    private final CnsReqRepoPort reqs;
    private final CnsArtRepoPort arts;
    private final LinkDpCmdHandler link;
    private final DpContactErasurePort contacts;

    public EraseDpConsentsHandler(CnsReqRepoPort reqs, CnsArtRepoPort arts, LinkDpCmdHandler link,
                                  DpContactErasurePort contacts) {
        this.reqs = reqs;
        this.arts = arts;
        this.link = link;
        this.contacts = contacts;
    }

    public Mono<Map<String, Object>> handle(String dpId, String email) {
        Mono<Long> claimed = email == null || email.isBlank() ? Mono.just(0L)
                : link.handle(new LinkDpCmd(dpId, ExtDpRefTyp.EMAIL, email.trim()));
        return claimed
                .then(withdraw(dpId))
                .flatMap(withdrawn -> decline(dpId).map(declined -> withdrawn + declined))
                .flatMap(changed -> Mono.zip(reqs.findByDpId(dpId).map(CnsReq::id).collectList(),
                                arts.findByDpId(dpId).map(CnsArt::id).collectList())
                        .flatMap(ids -> contacts.eraseContacts(ids.getT1(), ids.getT2())
                                .map(erased -> Map.<String, Object>of(
                                        "cnsReqIds", new ArrayList<>(ids.getT1()),
                                        "withdrawn", changed,
                                        "contactsErased", erased))));
    }

    /** Withdraws every consent in force, one at a time, each in its own save with its events. */
    private Mono<Long> withdraw(String dpId) {
        return arts.findByDpId(dpId)
                .filter(art -> art.sts() == CnsArtSts.ISSUED)
                .collectList()
                .flatMapMany(Flux::fromIterable)
                .concatMap(art -> {
                    art.revoke();
                    return arts.save(art);
                })
                .count();
    }

    /** Declines every request still waiting for the principal. */
    private Mono<Long> decline(String dpId) {
        return reqs.findByDpId(dpId)
                .filter(req -> req.sts() == CnsReqSts.PENDING)
                .collectList()
                .flatMapMany(Flux::fromIterable)
                .concatMap(req -> {
                    req.deny();
                    return reqs.save(req);
                })
                .count();
    }
}
