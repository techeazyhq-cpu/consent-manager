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

package com.techeazyhq.cm.cnsqry.app;

import java.time.Instant;
import java.util.Set;

import com.techeazyhq.cm.cnsqry.app.evt.CnsDeniedEvtPayload;
import com.techeazyhq.cm.cnsqry.app.evt.CnsNotificationFailedEvtPayload;
import com.techeazyhq.cm.cnsqry.app.evt.CnsDpLinkedEvtPayload;
import com.techeazyhq.cm.cnsqry.app.evt.CnsExpiredEvtPayload;
import com.techeazyhq.cm.cnsqry.app.evt.CnsGrantedEvtPayload;
import com.techeazyhq.cm.cnsqry.app.evt.CnsRevokedEvtPayload;
import com.techeazyhq.cm.cnsqry.app.evt.CnsReqCreatedEvtPayload;
import com.techeazyhq.cm.cnsqry.app.evt.DpRefPayload;
import com.techeazyhq.cm.cnsqry.app.evt.ScpPayload;
import com.techeazyhq.cm.cnsqry.dom.CnsArtRdMdl;
import com.techeazyhq.cm.cnsqry.dom.CnsArtRdMdlRepoPort;
import com.techeazyhq.cm.cnsqry.dom.CnsHstRdMdl;
import com.techeazyhq.cm.cnsqry.dom.CnsHstRdMdlRepoPort;
import com.techeazyhq.cm.cnsqry.dom.CnsLcySts;
import com.techeazyhq.cm.cnsqry.dom.PrjCkptPort;

import reactor.core.publisher.Mono;

/**
 * The idempotent Projector (plan §8, Observer pattern) — one handler method per event type,
 * each: dedupe by evtId (§7c "Kafka/Pulsar is at-least-once"), upsert {@code cns_art_rd_mdl},
 * append {@code cns_hst_rd_mdl}. Called from -infra's Pulsar Key_Shared consumer after it has
 * already deserialized the raw JSON into the matching typed payload record.
 */
public class Projector {

    private final CnsArtRdMdlRepoPort rdMdlRepo;
    private final CnsHstRdMdlRepoPort hstRepo;
    private final PrjCkptPort ckptPort;

    public Projector(CnsArtRdMdlRepoPort rdMdlRepo, CnsHstRdMdlRepoPort hstRepo, PrjCkptPort ckptPort) {
        this.rdMdlRepo = rdMdlRepo;
        this.hstRepo = hstRepo;
        this.ckptPort = ckptPort;
    }

    public Mono<Void> onCnsReqCreated(CnsReqCreatedEvtPayload evt) {
        return dedupe(evt.evtId(), () -> {
            Set<String> dCats = evt.scp() == null
                    ? Set.of()
                    : evt.scp().dCats().stream().map(ScpPayload.DCatPayload::code).collect(java.util.stream.Collectors.toSet());
            CnsArtRdMdl row = new CnsArtRdMdl(evt.cnsReqId(), null, evt.dfId(), dpIdOf(evt.dpRef()), evt.prps().code(),
                    dCats, CnsLcySts.PENDING, null, null, evt.occurredAt(), evt.occurredAt(), evt.ntcTitle(),
                    evt.ntcText());
            return rdMdlRepo.upsert(row)
                    .then(hstRepo.append(new CnsHstRdMdl(evt.cnsReqId(), null, CnsLcySts.PENDING, evt.occurredAt(), evt.evtId())));
        });
    }

    public Mono<Void> onCnsGranted(CnsGrantedEvtPayload evt) {
        return dedupe(evt.evtId(), () -> rdMdlRepo.findByCnsReqId(evt.cnsReqId())
                .flatMap(existing -> {
                    CnsArtRdMdl updated = existing.with(evt.cnsArtId(),
                            dpIdOf(evt.dpRef()) != null ? dpIdOf(evt.dpRef()) : existing.dpId(), CnsLcySts.ISSUED,
                            evt.valPrd().from(), evt.valPrd().until(), evt.occurredAt());
                    return rdMdlRepo.upsert(updated)
                            .then(hstRepo.append(new CnsHstRdMdl(evt.cnsReqId(), existing.lcySts(), CnsLcySts.ISSUED, evt.occurredAt(), evt.evtId())));
                }));
    }

    public Mono<Void> onCnsDenied(CnsDeniedEvtPayload evt) {
        return dedupe(evt.evtId(), () -> rdMdlRepo.findByCnsReqId(evt.cnsReqId())
                .flatMap(existing -> {
                    CnsArtRdMdl updated = withStatus(existing, CnsLcySts.DENIED, evt.occurredAt());
                    return rdMdlRepo.upsert(updated)
                            .then(hstRepo.append(new CnsHstRdMdl(evt.cnsReqId(), existing.lcySts(), CnsLcySts.DENIED, evt.occurredAt(), evt.evtId())));
                }));
    }

    public Mono<Void> onCnsNotificationFailed(CnsNotificationFailedEvtPayload evt) {
        return dedupe(evt.evtId(), () -> rdMdlRepo.findByCnsReqId(evt.cnsReqId())
                .flatMap(existing -> {
                    CnsArtRdMdl updated = withStatus(existing, CnsLcySts.NOTIFICATION_FAILED, evt.occurredAt());
                    return rdMdlRepo.upsert(updated)
                            .then(hstRepo.append(new CnsHstRdMdl(evt.cnsReqId(), existing.lcySts(),
                                    CnsLcySts.NOTIFICATION_FAILED, evt.occurredAt(), evt.evtId())));
                }));
    }

    public Mono<Void> onCnsRevoked(CnsRevokedEvtPayload evt) {
        return dedupe(evt.evtId(), () -> rdMdlRepo.findByCnsArtId(evt.cnsArtId())
                .flatMap(existing -> {
                    CnsArtRdMdl updated = withStatus(existing, CnsLcySts.REVOKED, evt.occurredAt());
                    return rdMdlRepo.upsert(updated)
                            .then(hstRepo.append(new CnsHstRdMdl(existing.cnsReqId(), existing.lcySts(), CnsLcySts.REVOKED, evt.occurredAt(), evt.evtId())));
                }));
    }

    public Mono<Void> onCnsExpired(CnsExpiredEvtPayload evt) {
        return dedupe(evt.evtId(), () -> rdMdlRepo.findByCnsArtId(evt.cnsArtId())
                .flatMap(existing -> {
                    CnsArtRdMdl updated = withStatus(existing, CnsLcySts.EXPIRED, evt.occurredAt());
                    return rdMdlRepo.upsert(updated)
                            .then(hstRepo.append(new CnsHstRdMdl(existing.cnsReqId(), existing.lcySts(), CnsLcySts.EXPIRED, evt.occurredAt(), evt.evtId())));
                }));
    }

    /** Plan §7a: the moment a person signs up, every request/artefact a DF raised against their email/phone appears in their dashboard. */
    public Mono<Void> onCnsDpLinked(CnsDpLinkedEvtPayload evt) {
        return dedupe(evt.evtId(), () -> rdMdlRepo.linkDp(evt.recordId(), evt.recordType(), evt.newDpId()));
    }

    private CnsArtRdMdl withStatus(CnsArtRdMdl existing, CnsLcySts newSts, Instant updatedAt) {
        return existing.with(existing.cnsArtId(), existing.dpId(), newSts, existing.validFrom(), existing.validUntil(),
                updatedAt);
    }

    private String dpIdOf(DpRefPayload dpRef) {
        return dpRef != null && dpRef.isLinked() ? dpRef.dpId() : null;
    }

    private Mono<Void> dedupe(String evtId, java.util.function.Supplier<Mono<Void>> action) {
        return ckptPort.alreadyProcessed(evtId)
                .flatMap(processed -> processed ? Mono.<Void>empty() : action.get().then(ckptPort.markProcessed(evtId)));
    }
}
