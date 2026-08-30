package com.techeazyhq.cm.cnscmd.app;

import org.springframework.transaction.annotation.Transactional;

import com.techeazyhq.cm.cnscmd.dom.CnsReq;
import com.techeazyhq.cm.cnscmd.dom.CnsReqDecidablePolicy;
import com.techeazyhq.cm.cnscmd.dom.CnsReqRepoPort;
import com.techeazyhq.cm.domkit.Spec;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Mono;

/** Stage 2 decided: deny (plan §7c step 3). {@code CnsReq.deny()} raises {@code CnsDeniedEvt} itself. */
public class DenyCnsReqCmdHandler implements CmdHandler<DenyCnsReqCmd, CnsReq> {

    private final CnsReqRepoPort cnsReqRepo;
    private final CnsReqDecidablePolicy decidablePolicy = new CnsReqDecidablePolicy();

    public DenyCnsReqCmdHandler(CnsReqRepoPort cnsReqRepo) {
        this.cnsReqRepo = cnsReqRepo;
    }

    @Override
    @Transactional
    public Mono<CnsReq> handle(DenyCnsReqCmd cmd) {
        return cnsReqRepo.findById(cmd.cnsReqId())
                .switchIfEmpty(Mono.error(new DomRuleViolation(404, "cns-req-not-found", "No CnsReq " + cmd.cnsReqId())))
                .flatMap(cnsReq -> {
                    Spec.SpecResult result = decidablePolicy.evaluate(cnsReq);
                    if (!result.satisfied()) {
                        return Mono.error(new DomRuleViolation(409, result.reasonCode(), "CnsReq " + cnsReq.id() + " cannot be decided"));
                    }
                    cnsReq.deny();
                    return cnsReqRepo.save(cnsReq);
                });
    }
}
