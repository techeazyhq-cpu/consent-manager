package com.techeazyhq.cm.cnsqry.app;

import java.time.Instant;
import java.util.Set;

import com.techeazyhq.cm.cnsqry.app.evt.CnsDeniedEvtPayload;
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
                    dCats, CnsLcySts.PENDING, null, null, evt.occurredAt(), evt.occurredAt());
            return rdMdlRepo.upsert(row)
                    .then(hstRepo.append(new CnsHstRdMdl(evt.cnsReqId(), null, CnsLcySts.PENDING, evt.occurredAt(), evt.evtId())));
        });
    }

    public Mono<Void> onCnsGranted(CnsGrantedEvtPayload evt) {
        return dedupe(evt.evtId(), () -> rdMdlRepo.findByCnsReqId(evt.cnsReqId())
                .flatMap(existing -> {
                    CnsArtRdMdl updated = new CnsArtRdMdl(existing.cnsReqId(), evt.cnsArtId(), existing.dfId(),
                            dpIdOf(evt.dpRef()) != null ? dpIdOf(evt.dpRef()) : existing.dpId(), existing.prpsCode(),
                            existing.dCatCodes(), CnsLcySts.ISSUED, evt.valPrd().from(), evt.valPrd().until(),
                            existing.createdAt(), evt.occurredAt());
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
        return new CnsArtRdMdl(existing.cnsReqId(), existing.cnsArtId(), existing.dfId(), existing.dpId(),
                existing.prpsCode(), existing.dCatCodes(), newSts, existing.validFrom(), existing.validUntil(),
                existing.createdAt(), updatedAt);
    }

    private String dpIdOf(DpRefPayload dpRef) {
        return dpRef != null && dpRef.isLinked() ? dpRef.dpId() : null;
    }

    private Mono<Void> dedupe(String evtId, java.util.function.Supplier<Mono<Void>> action) {
        return ckptPort.alreadyProcessed(evtId)
                .flatMap(processed -> processed ? Mono.<Void>empty() : action.get().then(ckptPort.markProcessed(evtId)));
    }
}
