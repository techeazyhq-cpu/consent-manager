package com.techeazyhq.cm.cnscmd.app;

import org.springframework.transaction.annotation.Transactional;

import com.techeazyhq.cm.cnscmd.dom.CnsArtRepoPort;
import com.techeazyhq.cm.cnscmd.dom.CnsDpLinkedEvt;
import com.techeazyhq.cm.cnscmd.dom.CnsReqRepoPort;
import com.techeazyhq.cm.cnscmd.dom.ObxPort;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Retroactively links every {@code CnsReq}/{@code CnsArt} raised against a not-yet-registered
 * DP (plan §7a), once that person signs up and the DP Portal calls this right after. Idempotent:
 * {@code linkDp} on an already-linked record is a no-op, so re-running is safe.
 */
public class LinkDpCmdHandler implements CmdHandler<LinkDpCmd, Long> {

    private final CnsReqRepoPort cnsReqRepo;
    private final CnsArtRepoPort cnsArtRepo;
    private final ObxPort obxPort;

    public LinkDpCmdHandler(CnsReqRepoPort cnsReqRepo, CnsArtRepoPort cnsArtRepo, ObxPort obxPort) {
        this.cnsReqRepo = cnsReqRepo;
        this.cnsArtRepo = cnsArtRepo;
        this.obxPort = obxPort;
    }

    @Override
    @Transactional
    public Mono<Long> handle(LinkDpCmd cmd) {
        Flux<Long> linkedReqs = cnsReqRepo.findUnlinkedByExtRef(cmd.extDpRefTyp(), cmd.extDpRefVal())
                .flatMap(cnsReq -> {
                    cnsReq.linkDp(cmd.newDpId());
                    return cnsReqRepo.save(cnsReq)
                            .then(obxPort.enqueue(
                                    CnsDpLinkedEvt.of(cnsReq.id(), "CnsReq", cmd.newDpId()), "CnsReq", cnsReq.id()))
                            .thenReturn(1L);
                });

        Flux<Long> linkedArts = cnsArtRepo.findUnlinkedByExtRef(cmd.extDpRefTyp(), cmd.extDpRefVal())
                .flatMap(cnsArt -> {
                    cnsArt.linkDp(cmd.newDpId());
                    return cnsArtRepo.save(cnsArt)
                            .then(obxPort.enqueue(
                                    CnsDpLinkedEvt.of(cnsArt.id(), "CnsArt", cmd.newDpId()), "CnsArt", cnsArt.id()))
                            .thenReturn(1L);
                });

        return Flux.concat(linkedReqs, linkedArts).count();
    }
}
