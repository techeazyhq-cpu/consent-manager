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

package com.techeazyhq.cm.aud.app;

import com.techeazyhq.cm.aud.dom.AudCheckpoint;
import com.techeazyhq.cm.aud.dom.AudCheckpointPort;
import com.techeazyhq.cm.aud.dom.AudCheckpointVerifier;
import com.techeazyhq.cm.aud.dom.AudEntry;
import com.techeazyhq.cm.aud.dom.AudLedgerPort;
import com.techeazyhq.cm.aud.dom.AudMerkle;
import com.techeazyhq.cm.aud.dom.AudRecord;
import com.techeazyhq.cm.aud.dom.TimestampAuthorityPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Mono;

import java.time.Clock;
import java.util.List;

/**
 * Checkpoints and external anchoring of the audit ledger (ADR-015): summarise new entries in a checkpoint, have a
 * timestamp authority sign each checkpoint's hash, prove that one entry is in an anchored checkpoint, and verify every
 * checkpoint against the ledger. A timestamp authority that is down only delays anchoring: the checkpoint is kept and
 * anchored on a later run.
 */
public class AudAnchoringService {

    private static final Logger LOG = LoggerFactory.getLogger(AudAnchoringService.class);

    /** Everything needed to show, offline, that an entry is part of the ledger as anchored. */
    public record InclusionProof(AudEntry entry, List<AudMerkle.Step> path, AudCheckpointPort.Anchored checkpoint) {
    }

    private final AudLedgerPort ledger;
    private final AudCheckpointPort checkpoints;
    private final TimestampAuthorityPort authority;
    private final Clock clock;

    public AudAnchoringService(AudLedgerPort ledger, AudCheckpointPort checkpoints, TimestampAuthorityPort authority,
                               Clock clock) {
        this.ledger = ledger;
        this.checkpoints = checkpoints;
        this.authority = authority;
        this.clock = clock;
    }

    /** Checkpoints the entries recorded since the last checkpoint, if any, then anchors every unanchored checkpoint. */
    public Mono<Void> checkpointAndAnchor() {
        return newCheckpoint().then(anchorPending());
    }

    private Mono<AudCheckpoint> newCheckpoint() {
        return checkpoints.latest().map(java.util.Optional::of).defaultIfEmpty(java.util.Optional.empty())
                .zipWith(ledger.head().map(AudEntry::seq).defaultIfEmpty(0L))
                .flatMap(state -> {
                    AudCheckpoint previous = state.getT1().orElse(null);
                    long from = previous == null ? 1 : previous.toSeq() + 1;
                    long head = state.getT2();
                    if (head < from) {
                        return Mono.empty();
                    }
                    return ledger.range(from, head).collectList()
                            .map(entries -> AudCheckpoint.over(previous, entries, clock.instant()))
                            .flatMap(cp -> checkpoints.save(cp).thenReturn(cp))
                            .doOnNext(cp -> LOG.info("Checkpoint {} covers entries {} to {}, hash {}", cp.id(), cp.fromSeq(),
                                    cp.toSeq(), cp.checkpointHash()));
                });
    }

    private Mono<Void> anchorPending() {
        return checkpoints.unanchored()
                .concatMap(cp -> authority.timestamp(cp.id(), cp.checkpointHash())
                        .flatMap(anchor -> checkpoints.saveAnchor(anchor).doOnSuccess(saved -> LOG.info(
                                "Checkpoint {} anchored by {} at {}", cp.id(), anchor.authorityUrl(), anchor.timestampedAt())))
                        .onErrorResume(err -> {
                            LOG.warn("Checkpoint {} not anchored yet, will retry: {}", cp.id(), err.getMessage());
                            return Mono.empty();
                        }))
                .then();
    }

    /** Entry {@code seq} with the Merkle path to the root of the checkpoint that covers it; empty if not yet covered. */
    public Mono<InclusionProof> proof(long seq) {
        return checkpoints.covering(seq).flatMap(anchored -> {
            AudCheckpoint cp = anchored.checkpoint();
            return ledger.range(cp.fromSeq(), cp.toSeq()).collectList().map(entries -> {
                int index = (int) (seq - cp.fromSeq());
                List<String> leaves = entries.stream().map(AudEntry::entryHash).toList();
                return new InclusionProof(entries.get(index), AudMerkle.proof(leaves, index), anchored);
            });
        });
    }

    public Mono<AudCheckpointVerifier.Verdict> verify() {
        return ledger.records().map(AudRecord::entry).collectList()
                .zipWith(checkpoints.all().collectList())
                .map(t -> AudCheckpointVerifier.verify(t.getT1(), t.getT2(), authority::verifiedImprint));
    }

    public reactor.core.publisher.Flux<AudCheckpointPort.Anchored> list() {
        return checkpoints.all();
    }
}
