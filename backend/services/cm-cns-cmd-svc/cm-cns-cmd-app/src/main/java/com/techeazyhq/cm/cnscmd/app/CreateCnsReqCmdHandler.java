package com.techeazyhq.cm.cnscmd.app;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.transaction.annotation.Transactional;

import com.techeazyhq.cm.cnscmd.dom.CnsReq;
import com.techeazyhq.cm.cnscmd.dom.CnsReqFactory;
import com.techeazyhq.cm.cnscmd.dom.CnsReqRepoPort;
import com.techeazyhq.cm.cnscmd.dom.DCat;
import com.techeazyhq.cm.cnscmd.dom.DfActivePort;
import com.techeazyhq.cm.cnscmd.dom.DfSubEligiblePolicy;
import com.techeazyhq.cm.cnscmd.dom.DpRef;
import com.techeazyhq.cm.cnscmd.dom.Prps;
import com.techeazyhq.cm.cnscmd.dom.Scp;
import com.techeazyhq.cm.domkit.Spec;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Mono;

/**
 * Stage 1: Capture (plan §7c step 1). Order of checks matters: DF-active first (cheap identity
 * check, existing pattern), then {@link DfSubEligiblePolicy} (§7b's hard subscription gate) —
 * both Circuit-Breaker-wrapped in their respective infra adapters — before anything is persisted.
 */
public class CreateCnsReqCmdHandler implements CmdHandler<CreateCnsReqCmd, CnsReq> {

    private final CnsReqRepoPort cnsReqRepo;
    private final DfActivePort dfActivePort;
    private final DfSubEligiblePolicy dfSubEligiblePolicy;

    public CreateCnsReqCmdHandler(CnsReqRepoPort cnsReqRepo, DfActivePort dfActivePort,
                                   DfSubEligiblePolicy dfSubEligiblePolicy) {
        this.cnsReqRepo = cnsReqRepo;
        this.dfActivePort = dfActivePort;
        this.dfSubEligiblePolicy = dfSubEligiblePolicy;
    }

    @Override
    @Transactional
    public Mono<CnsReq> handle(CreateCnsReqCmd cmd) {
        DpRef dpRef = resolveDpRef(cmd);

        return dfActivePort.isActive(cmd.dfId())
                .flatMap(active -> active
                        ? Mono.empty()
                        : Mono.error(new DomRuleViolation(404, "df-not-active", "DF " + cmd.dfId() + " is not active")))
                .then(dfSubEligiblePolicy.evaluate(cmd.dfId()))
                .flatMap(result -> requireEligible(cmd.dfId(), result))
                .then(Mono.defer(() -> {
                    Scp scp = new Scp(cmd.dCatCodes().stream().map(DCat::new).collect(Collectors.toSet()));
                    CnsReq cnsReq = CnsReqFactory.create(
                            cmd.dfId(), dpRef, new Prps(cmd.prpsCode()), scp, cmd.ntcTitle(), cmd.ntcText());
                    return cnsReqRepo.save(cnsReq);
                }));
    }

    private Mono<Void> requireEligible(String dfId, Spec.SpecResult result) {
        if (result.satisfied()) {
            return Mono.empty();
        }
        int status = "quota-exceeded".equals(result.reasonCode()) ? 429 : 402;
        return Mono.error(new DomRuleViolation(
                status, result.reasonCode(), "DF " + dfId + " is not eligible to raise a new consent request"));
    }

    private DpRef resolveDpRef(CreateCnsReqCmd cmd) {
        boolean hasDpId = cmd.dpId() != null && !cmd.dpId().isBlank();
        boolean hasExtRef = cmd.extDpRefTyp() != null && cmd.extDpRefVal() != null && !cmd.extDpRefVal().isBlank();
        if (hasDpId == hasExtRef) {
            throw new IllegalArgumentException("Exactly one of dpId or (extDpRefTyp, extDpRefVal) must be supplied");
        }
        return hasDpId ? DpRef.linked(cmd.dpId()) : DpRef.unlinked(cmd.extDpRefTyp(), cmd.extDpRefVal());
    }
}
