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

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AudMerkleTest {

    private static final Instant NOW = Instant.parse("2026-10-10T12:00:00Z");

    private static List<String> leaves(int n) {
        return IntStream.range(0, n).mapToObj(i -> AudChain.sha256Hex("entry-" + i)).toList();
    }

    @Test
    void everyLeafOfTreesOfEverySizeProvesItsWayToTheRoot() {
        for (int n = 1; n <= 17; n++) {
            List<String> leaves = leaves(n);
            String root = AudMerkle.root(leaves);
            for (int i = 0; i < n; i++) {
                assertTrue(AudMerkle.verify(leaves.get(i), AudMerkle.proof(leaves, i), root), "leaf " + i + " of " + n);
            }
        }
    }

    @Test
    void aProofDoesNotWorkForAnotherLeafOrAnotherRoot() {
        List<String> leaves = leaves(7);
        String root = AudMerkle.root(leaves);
        List<AudMerkle.Step> proofOf3 = AudMerkle.proof(leaves, 3);

        assertFalse(AudMerkle.verify(leaves.get(4), proofOf3, root));
        assertFalse(AudMerkle.verify(leaves.get(3), proofOf3, AudMerkle.root(leaves(8))));
    }

    @Test
    void theRootDependsOnEveryLeafAndTheirOrder() {
        List<String> leaves = new ArrayList<>(leaves(5));
        String root = AudMerkle.root(leaves);

        List<String> swapped = new ArrayList<>(leaves);
        swapped.set(1, leaves.get(2));
        swapped.set(2, leaves.get(1));
        List<String> changed = new ArrayList<>(leaves);
        changed.set(4, AudChain.sha256Hex("something else"));

        assertNotEquals(root, AudMerkle.root(swapped));
        assertNotEquals(root, AudMerkle.root(changed));
        assertNotEquals(AudMerkle.root(leaves(2)), AudMerkle.root(List.of(leaves(1).get(0))));
    }

    @Test
    void aProofIsAboutAsLongAsTheTreeIsDeep() {
        assertEquals(0, AudMerkle.proof(leaves(1), 0).size());
        assertEquals(10, AudMerkle.proof(leaves(1024), 513).size());
        assertThrows(IllegalArgumentException.class, () -> AudMerkle.proof(leaves(3), 3));
    }

    private static List<AudEntry> chain(int length) {
        List<AudEntry> entries = new ArrayList<>();
        long seq = 0;
        String hash = AudChain.GENESIS_HASH;
        for (int n = 1; n <= length; n++) {
            AudEntry entry = AudChain.link(seq, hash, new AudDraft(UUID.randomUUID(), "CnsGrantedEvt", NOW, "req", "art-" + n,
                    "df", null, "{}"), NOW);
            entries.add(entry);
            seq = entry.seq();
            hash = entry.entryHash();
        }
        return entries;
    }

    @Test
    void checkpointsChainAndVerifyAgainstTheLedgerTheyCover() {
        List<AudEntry> entries = chain(10);
        AudCheckpoint first = AudCheckpoint.over(null, entries.subList(0, 4), NOW);
        AudCheckpoint second = AudCheckpoint.over(first, entries.subList(4, 10), NOW.plusSeconds(60));
        List<AudCheckpointPort.Anchored> checkpoints = List.of(
                new AudCheckpointPort.Anchored(first, new AudAnchor(1, "tsa", NOW, first.checkpointHash().getBytes())),
                new AudCheckpointPort.Anchored(second, null));

        AudCheckpointVerifier.Verdict verdict = AudCheckpointVerifier.verify(entries, checkpoints, String::new);

        assertEquals(AudChain.GENESIS_HASH, first.prevCheckpointHash());
        assertEquals(first.checkpointHash(), second.prevCheckpointHash());
        assertEquals(5, second.fromSeq());
        assertTrue(verdict.intact(), verdict.reason());
        assertEquals(1, verdict.anchored());
        assertEquals(4, verdict.anchoredUpToSeq());
        assertNull(verdict.firstBrokenId());
    }

    @Test
    void aLedgerRewrittenUnderAnAnchoredCheckpointIsCaught() {
        List<AudEntry> original = chain(6);
        AudCheckpoint cp = AudCheckpoint.over(null, original, NOW);
        AudAnchor anchor = new AudAnchor(1, "tsa", NOW, cp.checkpointHash().getBytes());
        List<AudEntry> rewritten = chain(6);

        AudCheckpointVerifier.Verdict verdict = AudCheckpointVerifier.verify(rewritten,
                List.of(new AudCheckpointPort.Anchored(cp, anchor)), String::new);

        assertFalse(verdict.intact());
        assertTrue(verdict.reason().contains("Merkle root"));
    }

    @Test
    void aCheckpointRecomputedToFitARewrittenLedgerNoLongerMatchesItsTimestampToken() {
        List<AudEntry> original = chain(6);
        AudCheckpoint genuine = AudCheckpoint.over(null, original, NOW);
        AudAnchor anchor = new AudAnchor(1, "tsa", NOW, genuine.checkpointHash().getBytes());
        List<AudEntry> rewritten = chain(6);
        AudCheckpoint refitted = AudCheckpoint.over(null, rewritten, NOW);

        AudCheckpointVerifier.Verdict verdict = AudCheckpointVerifier.verify(rewritten,
                List.of(new AudCheckpointPort.Anchored(refitted, anchor)), String::new);

        assertFalse(verdict.intact());
        assertTrue(verdict.reason().contains("different hash"));
    }

    @Test
    void anInvalidTimestampTokenIsReported() {
        List<AudEntry> entries = chain(2);
        AudCheckpoint cp = AudCheckpoint.over(null, entries, NOW);

        AudCheckpointVerifier.Verdict verdict = AudCheckpointVerifier.verify(entries,
                List.of(new AudCheckpointPort.Anchored(cp, new AudAnchor(1, "tsa", NOW, new byte[0]))),
                token -> {
                    throw new IllegalArgumentException("bad signature");
                });

        assertFalse(verdict.intact());
        assertTrue(verdict.reason().contains("not validly signed"));
    }
}
