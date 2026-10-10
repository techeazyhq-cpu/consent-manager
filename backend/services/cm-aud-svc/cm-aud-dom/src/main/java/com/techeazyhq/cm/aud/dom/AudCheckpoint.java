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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * A summary of the ledger up to {@code toSeq} (ADR-015): the Merkle root of the entries since the previous checkpoint,
 * the chain head it ends at, and the previous checkpoint's hash, so checkpoints form a chain of their own. Its
 * {@code checkpointHash} is what an external timestamp authority signs.
 *
 * <p>{@code checkpointHash} is the SHA-256, as lower-case hex, of these lines joined by {@code \n}:
 * {@code cm-aud-checkpoint-v1}, id, fromSeq, toSeq, merkleRoot, headHash, prevCheckpointHash (64 zeros for the
 * first), createdAt (ISO-8601, microseconds).
 */
public record AudCheckpoint(long id, long fromSeq, long toSeq, String merkleRoot, String headHash,
                            String prevCheckpointHash, Instant createdAt, String checkpointHash) {

    private static final String VERSION = "cm-aud-checkpoint-v1";

    /** The checkpoint after {@code previous} (null for the first) over {@code entries}, consecutive and in order. */
    public static AudCheckpoint over(AudCheckpoint previous, List<AudEntry> entries, Instant now) {
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("A checkpoint covers at least one entry");
        }
        long id = previous == null ? 1 : previous.id() + 1;
        String prevHash = previous == null ? AudChain.GENESIS_HASH : previous.checkpointHash();
        AudEntry first = entries.get(0);
        AudEntry last = entries.get(entries.size() - 1);
        String root = AudMerkle.root(entries.stream().map(AudEntry::entryHash).toList());
        Instant createdAt = now.truncatedTo(ChronoUnit.MICROS);
        return new AudCheckpoint(id, first.seq(), last.seq(), root, last.entryHash(), prevHash, createdAt,
                hash(id, first.seq(), last.seq(), root, last.entryHash(), prevHash, createdAt));
    }

    /** Recomputes the hash from the fields, as a verifier would. */
    public String recomputedHash() {
        return hash(id, fromSeq, toSeq, merkleRoot, headHash, prevCheckpointHash, createdAt);
    }

    private static String hash(long id, long fromSeq, long toSeq, String merkleRoot, String headHash, String prevHash,
                               Instant createdAt) {
        return AudChain.sha256Hex(String.join("\n", VERSION, Long.toString(id), Long.toString(fromSeq),
                Long.toString(toSeq), merkleRoot, headHash, prevHash, createdAt.truncatedTo(ChronoUnit.MICROS).toString()));
    }
}
