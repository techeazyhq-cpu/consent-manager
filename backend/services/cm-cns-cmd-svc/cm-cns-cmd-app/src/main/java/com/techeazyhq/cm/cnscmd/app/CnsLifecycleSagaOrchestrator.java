package com.techeazyhq.cm.cnscmd.app;

import org.springframework.transaction.annotation.Transactional;

import com.techeazyhq.cm.cnscmd.dom.CnsReq;
import com.techeazyhq.cm.cnscmd.dom.CnsReqRepoPort;
import com.techeazyhq.cm.cnscmd.dom.CnsReqSts;
import com.techeazyhq.cm.cnscmd.dom.NtfCmdPort;

import reactor.core.publisher.Mono;

/**
 * Process Manager (plan §7c, §12) for the parts of the consent lifecycle that span services:
 * driving {@code cm-ntf-svc} notifications after each write-side transition. The write-side
 * state transitions themselves (create/approve/deny/revoke) are handled directly by their
 * own {@link CmdHandler}s — this orchestrator is specifically the "and then notify" half of
 * each saga step, invoked by -infra's Pulsar consumers subscribed to this service's own
 * {@code evt-cns-*} topics (so a notification failure never blocks the write path itself).
 * Crash-recovery re-drive from {@code sga_state} is a -infra concern (persistence + a
 * startup sweep), not modeled in this class.
 */
public class CnsLifecycleSagaOrchestrator {

    private final CnsReqRepoPort cnsReqRepo;
    private final NtfCmdPort ntfCmdPort;

    public CnsLifecycleSagaOrchestrator(CnsReqRepoPort cnsReqRepo, NtfCmdPort ntfCmdPort) {
        this.cnsReqRepo = cnsReqRepo;
        this.ntfCmdPort = ntfCmdPort;
    }

    /** Step 2 (plan §7c): on CnsReqCreatedEvt, notify the DP across every available channel. */
    public Mono<Void> onCnsReqCreated(String cnsReqId) {
        return cnsReqRepo.findById(cnsReqId)
                .flatMap(cnsReq -> ntfCmdPort.notifyDp(cnsReq)
                        .onErrorResume(err -> markNotificationFailed(cnsReq)));
    }

    /** Step 4 (plan §7c): on grant/deny, notify the DF. */
    public Mono<Void> onCnsDecided(String dfId, String cnsReqId, CnsReqSts outcome) {
        return ntfCmdPort.notifyDf(dfId, cnsReqId, outcome.name());
    }

    /** RevokeConsent's notify leg (plan §7c step 5). */
    public Mono<Void> onCnsRevoked(String dfId, String cnsArtId) {
        return ntfCmdPort.notifyDf(dfId, cnsArtId, "REVOKED");
    }

    @Transactional
    private Mono<Void> markNotificationFailed(CnsReq cnsReq) {
        cnsReq.markNotificationFailed();
        return cnsReqRepo.save(cnsReq).then();
    }
}
