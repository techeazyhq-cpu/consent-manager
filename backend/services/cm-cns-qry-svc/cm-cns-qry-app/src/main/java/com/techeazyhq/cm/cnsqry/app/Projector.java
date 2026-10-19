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
import java.util.function.Supplier;

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
                    evt.ntcText(), null);
            return rdMdlRepo.upsert(row)
                    .then(hstRepo.append(new CnsHstRdMdl(evt.cnsReqId(), null, CnsLcySts.PENDING, evt.occurredAt(), evt.evtId())));
        });
    }

    public Mono<Void> onCnsGranted(CnsGrantedEvtPayload evt) {
        return dedupe(evt.evtId(), () -> rowOf(rdMdlRepo.findByCnsReqId(evt.cnsReqId()), "request " + evt.cnsReqId())
                .flatMap(existing -> unlessStale(existing, evt.occurredAt(), () -> {
                    // partial consent (ADR-026): what was granted; a grant from before it gave everything asked
                    Set<String> granted = evt.scp() == null ? existing.dCatCodes()
                            : evt.scp().dCats().stream().map(ScpPayload.DCatPayload::code)
                                    .collect(java.util.stream.Collectors.toSet());
                    CnsArtRdMdl updated = existing.with(evt.cnsArtId(),
                            dpIdOf(evt.dpRef()) != null ? dpIdOf(evt.dpRef()) : existing.dpId(), CnsLcySts.ISSUED,
                            evt.valPrd().from(), evt.valPrd().until(), evt.occurredAt()).granted(granted);
                    return rdMdlRepo.upsert(updated)
                            .then(hstRepo.append(new CnsHstRdMdl(evt.cnsReqId(), existing.lcySts(), CnsLcySts.ISSUED, evt.occurredAt(), evt.evtId())));
                })));
    }

    public Mono<Void> onCnsDenied(CnsDeniedEvtPayload evt) {
        return dedupe(evt.evtId(), () -> rowOf(rdMdlRepo.findByCnsReqId(evt.cnsReqId()), "request " + evt.cnsReqId())
                .flatMap(existing -> transition(existing, CnsLcySts.DENIED, evt.occurredAt(), evt.evtId())));
    }

    public Mono<Void> onCnsNotificationFailed(CnsNotificationFailedEvtPayload evt) {
        return dedupe(evt.evtId(), () -> rowOf(rdMdlRepo.findByCnsReqId(evt.cnsReqId()), "request " + evt.cnsReqId())
                .flatMap(existing -> transition(existing, CnsLcySts.NOTIFICATION_FAILED, evt.occurredAt(), evt.evtId())));
    }

    public Mono<Void> onCnsRevoked(CnsRevokedEvtPayload evt) {
        return dedupe(evt.evtId(), () -> rowOf(rdMdlRepo.findByCnsArtId(evt.cnsArtId()), "consent " + evt.cnsArtId())
                .flatMap(existing -> transition(existing, CnsLcySts.REVOKED, evt.occurredAt(), evt.evtId())));
    }

    public Mono<Void> onCnsExpired(CnsExpiredEvtPayload evt) {
        return dedupe(evt.evtId(), () -> rowOf(rdMdlRepo.findByCnsArtId(evt.cnsArtId()), "consent " + evt.cnsArtId())
                .flatMap(existing -> transition(existing, CnsLcySts.EXPIRED, evt.occurredAt(), evt.evtId())));
    }

    /** Plan §7a: the moment a person signs up, every request/artefact a DF raised against their email/phone appears in their dashboard. */
    public Mono<Void> onCnsDpLinked(CnsDpLinkedEvtPayload evt) {
        Mono<CnsArtRdMdl> record = "CnsReq".equals(evt.recordType())
                ? rdMdlRepo.findByCnsReqId(evt.recordId()) : rdMdlRepo.findByCnsArtId(evt.recordId());
        return dedupe(evt.evtId(), () -> rowOf(record, evt.recordType() + " " + evt.recordId())
                .then(rdMdlRepo.linkDp(evt.recordId(), evt.recordType(), evt.newDpId())));
    }

    /**
     * The row an event changes, or {@link NotYetProjected} if it isn't there yet. Each event type has its own topic, so
     * a grant can arrive before its request's creation; it then fails, isn't marked processed, and comes back once the
     * creation is projected. It used to be dropped and marked processed, leaving a given consent PENDING (#219).
     */
    private static Mono<CnsArtRdMdl> rowOf(Mono<CnsArtRdMdl> lookup, String what) {
        return lookup.switchIfEmpty(Mono.error(() -> new NotYetProjected(what)));
    }

    private Mono<Void> transition(CnsArtRdMdl existing, CnsLcySts newSts, Instant occurredAt, String evtId) {
        return unlessStale(existing, occurredAt, () -> rdMdlRepo.upsert(withStatus(existing, newSts, occurredAt))
                .then(hstRepo.append(new CnsHstRdMdl(existing.cnsReqId(), existing.lcySts(), newSts, occurredAt, evtId))));
    }

    /**
     * A change older than the row's last one is not applied: arriving late, a notification failure would otherwise
     * undo a grant made after it (#219). Every status event comes from cm-cns-cmd-svc's clock.
     */
    private static Mono<Void> unlessStale(CnsArtRdMdl existing, Instant occurredAt, Supplier<Mono<Void>> change) {
        boolean stale = existing.updatedAt() != null && occurredAt != null && existing.updatedAt().isAfter(occurredAt);
        return stale ? Mono.empty() : change.get();
    }

    private CnsArtRdMdl withStatus(CnsArtRdMdl existing, CnsLcySts newSts, Instant updatedAt) {
        return existing.with(existing.cnsArtId(), existing.dpId(), newSts, existing.validFrom(), existing.validUntil(),
                updatedAt);
    }

    private String dpIdOf(DpRefPayload dpRef) {
        return dpRef != null && dpRef.isLinked() ? dpRef.dpId() : null;
    }

    /** Marked processed only once its change succeeded, so a failed one is redelivered (#219). */
    private Mono<Void> dedupe(String evtId, Supplier<Mono<Void>> action) {
        return ckptPort.alreadyProcessed(evtId)
                .flatMap(processed -> processed ? Mono.<Void>empty()
                        : action.get().then(Mono.defer(() -> ckptPort.markProcessed(evtId))));
    }

    /** An event about a row that isn't projected yet: to be redelivered, never acknowledged as done. */
    public static final class NotYetProjected extends RuntimeException {

        public NotYetProjected(String what) {
            super("Not projected yet: " + what, null, false, false);
        }
    }
}
