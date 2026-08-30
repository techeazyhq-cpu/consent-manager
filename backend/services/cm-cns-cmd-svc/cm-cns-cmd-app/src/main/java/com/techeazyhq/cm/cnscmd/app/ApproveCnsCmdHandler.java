package com.techeazyhq.cm.cnscmd.app;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.transaction.annotation.Transactional;

import com.techeazyhq.cm.cnscmd.dom.CnsArt;
import com.techeazyhq.cm.cnscmd.dom.CnsArtFactory;
import com.techeazyhq.cm.cnscmd.dom.CnsArtRepoPort;
import com.techeazyhq.cm.cnscmd.dom.CnsGrantedEvt;
import com.techeazyhq.cm.cnscmd.dom.CnsReq;
import com.techeazyhq.cm.cnscmd.dom.CnsReqDecidablePolicy;
import com.techeazyhq.cm.cnscmd.dom.CnsReqRepoPort;
import com.techeazyhq.cm.cnscmd.dom.ObxPort;
import com.techeazyhq.cm.cnscmd.dom.ValPrd;
import com.techeazyhq.cm.domkit.Spec;
import com.techeazyhq.cm.seckit.CnsTknClaims;
import com.techeazyhq.cm.seckit.CnsTknSigner;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Mono;

/**
 * Stage 2 decided: approve (plan §7c step 3). Creates the {@link CnsArt}, signs its
 * {@code CnsTkn}, and explicitly enqueues the composed {@code CnsGrantedEvt} via
 * {@link ObxPort} since it needs data from both aggregates plus the signature — it isn't
 * one aggregate's own {@code pullEvts()} event.
 */
public class ApproveCnsCmdHandler implements CmdHandler<ApproveCnsReqCmd, CnsArt> {

    /** Phase-1 default artefact validity — a fixed platform-wide policy, not yet per-DF/per-purpose configurable. */
    private static final Duration DEFAULT_VALIDITY = Duration.ofDays(365);

    private final CnsReqRepoPort cnsReqRepo;
    private final CnsArtRepoPort cnsArtRepo;
    private final ObxPort obxPort;
    private final CnsTknSigner cnsTknSigner;
    private final CnsReqDecidablePolicy decidablePolicy = new CnsReqDecidablePolicy();

    public ApproveCnsCmdHandler(CnsReqRepoPort cnsReqRepo, CnsArtRepoPort cnsArtRepo, ObxPort obxPort,
                                 CnsTknSigner cnsTknSigner) {
        this.cnsReqRepo = cnsReqRepo;
        this.cnsArtRepo = cnsArtRepo;
        this.obxPort = obxPort;
        this.cnsTknSigner = cnsTknSigner;
    }

    @Override
    @Transactional
    public Mono<CnsArt> handle(ApproveCnsReqCmd cmd) {
        return cnsReqRepo.findById(cmd.cnsReqId())
                .switchIfEmpty(Mono.error(new DomRuleViolation(404, "cns-req-not-found", "No CnsReq " + cmd.cnsReqId())))
                .flatMap(this::requireDecidable)
                .flatMap(cnsReq -> {
                    cnsReq.approve();
                    CnsArt cnsArt = CnsArtFactory.issue(
                            cnsReq.id(), cnsReq.dfId(), cnsReq.dpRef(), cnsReq.prps(), cnsReq.scp(),
                            ValPrd.of(Instant.now(), DEFAULT_VALIDITY));

                    String cnsTkn = cnsTknSigner.sign(new CnsTknClaims(
                            dpIdOf(cnsReq), cnsReq.dfId(), cnsReq.prps().code(), CnsTknClaims.AccessType.ALLOW,
                            List.of(), cnsArt.valPrd().until()));
                    cnsArt.attachSignedTkn(cnsTkn);

                    return cnsReqRepo.save(cnsReq)
                            .then(cnsArtRepo.save(cnsArt))
                            .flatMap(savedArt -> obxPort
                                    .enqueue(CnsGrantedEvt.of(cnsReq.id(), savedArt.id(), cnsReq.dfId(), cnsReq.dpRef(),
                                                    cnsTkn, savedArt.valPrd()),
                                            "CnsArt", savedArt.id())
                                    .thenReturn(savedArt));
                });
    }

    private Mono<CnsReq> requireDecidable(CnsReq cnsReq) {
        Spec.SpecResult result = decidablePolicy.evaluate(cnsReq);
        if (!result.satisfied()) {
            return Mono.error(new DomRuleViolation(409, result.reasonCode(), "CnsReq " + cnsReq.id() + " cannot be decided"));
        }
        return Mono.just(cnsReq);
    }

    private String dpIdOf(CnsReq cnsReq) {
        return switch (cnsReq.dpRef()) {
            case com.techeazyhq.cm.cnscmd.dom.DpRef.LnkdDpRef lnkd -> lnkd.dpId();
            case com.techeazyhq.cm.cnscmd.dom.DpRef.UnlnkDpRef unlnk ->
                    throw new IllegalStateException("Cannot approve a CnsReq whose DP is still unlinked");
        };
    }
}
