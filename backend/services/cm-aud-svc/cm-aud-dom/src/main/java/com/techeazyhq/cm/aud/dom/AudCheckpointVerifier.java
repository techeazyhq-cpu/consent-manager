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

package com.techeazyhq.cm.aud.dom;

import java.util.List;
import java.util.function.Function;

/**
 * Checks the checkpoints against the ledger (ADR-015): they follow on from each other with no gap, each one's hash
 * matches its fields and points at the previous checkpoint, its Merkle root and head match the entries it covers, and
 * its anchor, if any, is a validly signed timestamp token for exactly its hash. A rewritten ledger cannot pass this
 * without also forging the timestamp authority's signature.
 */
public final class AudCheckpointVerifier {

    /**
     * @param intact               whether every checkpoint and anchor held
     * @param checkpoints          how many checkpoints were checked
     * @param anchored             how many of them carry a valid timestamp token
     * @param firstBrokenId        the first checkpoint that failed, or null
     * @param reason               why, or null
     * @param anchoredUpToSeq      the last entry covered by an anchored checkpoint (0 if none)
     */
    public record Verdict(boolean intact, long checkpoints, long anchored, Long firstBrokenId, String reason,
                          long anchoredUpToSeq) {
    }

    private AudCheckpointVerifier() {
    }

    /**
     * @param entries       every ledger entry, in sequence order (already checked by {@link AudChainVerifier})
     * @param checkpoints   every checkpoint in order, with its anchor
     * @param imprintOf     the verified SHA-256 a timestamp token was issued for; throws for an invalid token
     */
    public static Verdict verify(List<AudEntry> entries, List<AudCheckpointPort.Anchored> checkpoints,
                                 Function<byte[], String> imprintOf) {
        long expectedFrom = 1;
        String expectedPrev = AudChain.GENESIS_HASH;
        long anchored = 0;
        long anchoredUpTo = 0;
        for (AudCheckpointPort.Anchored item : checkpoints) {
            AudCheckpoint cp = item.checkpoint();
            String failure = failure(cp, expectedFrom, expectedPrev, entries);
            if (failure == null && item.anchor() != null) {
                failure = anchorFailure(cp, item.anchor(), imprintOf);
                if (failure == null) {
                    anchored++;
                    anchoredUpTo = cp.toSeq();
                }
            }
            if (failure != null) {
                return new Verdict(false, cp.id(), anchored, cp.id(), "Checkpoint " + cp.id() + " " + failure, anchoredUpTo);
            }
            expectedFrom = cp.toSeq() + 1;
            expectedPrev = cp.checkpointHash();
        }
        return new Verdict(true, checkpoints.size(), anchored, null, null, anchoredUpTo);
    }

    private static String failure(AudCheckpoint cp, long expectedFrom, String expectedPrev, List<AudEntry> entries) {
        if (cp.fromSeq() != expectedFrom || cp.toSeq() < cp.fromSeq()) {
            return "does not start where the previous one ended (entry " + expectedFrom + ")";
        }
        if (!cp.prevCheckpointHash().equals(expectedPrev)) {
            return "does not point at the previous checkpoint's hash";
        }
        if (!cp.checkpointHash().equals(cp.recomputedHash())) {
            return "no longer matches its own hash";
        }
        if (cp.toSeq() > entries.size()) {
            return "covers entries the ledger no longer has";
        }
        List<AudEntry> covered = entries.subList((int) cp.fromSeq() - 1, (int) cp.toSeq());
        if (!AudMerkle.root(covered.stream().map(AudEntry::entryHash).toList()).equals(cp.merkleRoot())) {
            return "has a Merkle root the entries no longer produce";
        }
        if (!covered.get(covered.size() - 1).entryHash().equals(cp.headHash())) {
            return "ends at a chain head the ledger no longer has";
        }
        return null;
    }

    private static String anchorFailure(AudCheckpoint cp, AudAnchor anchor, Function<byte[], String> imprintOf) {
        try {
            return imprintOf.apply(anchor.token()).equals(cp.checkpointHash())
                    ? null : "has a timestamp token issued for a different hash";
        } catch (RuntimeException invalid) {
            return "has a timestamp token that is not validly signed: " + invalid.getMessage();
        }
    }
}
