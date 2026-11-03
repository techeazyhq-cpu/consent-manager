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

import java.time.Clock;

import org.springframework.transaction.annotation.Transactional;

import com.techeazyhq.cm.cnscmd.dom.CnsReq;
import com.techeazyhq.cm.cnscmd.dom.CnsReqRepoPort;
import com.techeazyhq.cm.cnscmd.dom.CnsReqSts;
import com.techeazyhq.cm.cnscmd.dom.NtfCmdPort;
import com.techeazyhq.cm.cnscmd.dom.SgaStatePort;
import com.techeazyhq.cm.cnscmd.dom.SgaStep;

import reactor.core.publisher.Mono;

/**
 * Process Manager (plan §7c, §12) for the parts of the consent lifecycle that span services:
 * driving {@code cm-ntf-svc} notifications after each write-side transition. The write-side
 * state transitions themselves (create/approve/deny/revoke) are handled directly by their
 * own {@link CmdHandler}s — this orchestrator is specifically the "and then notify" half of
 * each saga step, invoked by -infra's Pulsar consumers subscribed to this service's own
 * {@code evt-cns-*} topics (so a notification failure never blocks the write path itself).
 *
 * <p>Each leg records itself in {@code sga_state} (ADR-005): started, then done or failed with the error. A failed
 * leg still fails, so Pulsar redelivers it; {@link SgaRedriveJob} runs again the legs that stay unfinished.
 */
public class CnsLifecycleSagaOrchestrator {

    private final CnsReqRepoPort cnsReqRepo;
    private final NtfCmdPort ntfCmdPort;
    private final SgaStatePort sgaState;
    private final Clock clock;

    public CnsLifecycleSagaOrchestrator(CnsReqRepoPort cnsReqRepo, NtfCmdPort ntfCmdPort) {
        this(cnsReqRepo, ntfCmdPort, SgaStatePort.NONE, Clock.systemUTC());
    }

    public CnsLifecycleSagaOrchestrator(CnsReqRepoPort cnsReqRepo, NtfCmdPort ntfCmdPort, SgaStatePort sgaState,
                                        Clock clock) {
        this.cnsReqRepo = cnsReqRepo;
        this.ntfCmdPort = ntfCmdPort;
        this.sgaState = sgaState;
        this.clock = clock;
    }

    /** Step 2 (plan §7c): on CnsReqCreatedEvt, notify the DP across every available channel. */
    public Mono<Void> onCnsReqCreated(String cnsReqId) {
        return track(SgaStep.of(SgaStep.Typ.NOTIFY_DP, cnsReqId, null, null), cnsReqRepo.findById(cnsReqId)
                .flatMap(cnsReq -> ntfCmdPort.notifyDp(cnsReq)
                        .onErrorResume(err -> markNotificationFailed(cnsReq))));
    }

    /**
     * Step 2's other outcome: cm-ntf-svc could not reach the DP (their email or SMS was refused for good). A request
     * the DP has meanwhile decided, after signing up and finding it on their dashboard, keeps its decision.
     */
    public Mono<Void> onDpDeliveryFailed(String cnsReqId) {
        return track(SgaStep.of(SgaStep.Typ.DP_UNREACHABLE, cnsReqId, null, null), cnsReqRepo.findById(cnsReqId)
                .filter(cnsReq -> cnsReq.sts() == CnsReqSts.PENDING)
                .flatMap(this::markNotificationFailed));
    }

    /** Step 4 (plan §7c): on grant/deny, notify the DF. */
    public Mono<Void> onCnsDecided(String dfId, String cnsReqId, CnsReqSts outcome) {
        return track(SgaStep.of(SgaStep.Typ.NOTIFY_DF_DECIDED, cnsReqId, dfId, outcome.name()),
                Mono.defer(() -> ntfCmdPort.notifyDf(dfId, cnsReqId, outcome.name())));
    }

    /** RevokeConsent's notify leg (plan §7c step 5). */
    public Mono<Void> onCnsRevoked(String dfId, String cnsArtId) {
        return track(SgaStep.of(SgaStep.Typ.NOTIFY_DF_REVOKED, cnsArtId, dfId, "REVOKED"),
                Mono.defer(() -> ntfCmdPort.notifyDf(dfId, cnsArtId, "REVOKED")));
    }

    /** Runs a recorded leg again, from what its row holds. */
    public Mono<Void> redrive(SgaStep step) {
        return switch (step.typ()) {
            case NOTIFY_DP -> onCnsReqCreated(step.cnsId());
            case DP_UNREACHABLE -> onDpDeliveryFailed(step.cnsId());
            case NOTIFY_DF_DECIDED -> onCnsDecided(step.dfId(), step.cnsId(), CnsReqSts.valueOf(step.outcome()));
            case NOTIFY_DF_REVOKED -> onCnsRevoked(step.dfId(), step.cnsId());
        };
    }

    private Mono<Void> track(SgaStep step, Mono<?> leg) {
        return sgaState.started(step, clock.instant())
                .then(leg)
                .then(Mono.defer(() -> sgaState.done(step.sagaId(), clock.instant())))
                .onErrorResume(err -> sgaState.failed(step.sagaId(), String.valueOf(err.getMessage()), clock.instant())
                        .then(Mono.error(err)));
    }

    @Transactional
    private Mono<Void> markNotificationFailed(CnsReq cnsReq) {
        cnsReq.markNotificationFailed();
        return cnsReqRepo.save(cnsReq).then();
    }
}
