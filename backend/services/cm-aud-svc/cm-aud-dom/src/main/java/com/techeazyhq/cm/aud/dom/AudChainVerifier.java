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

/**
 * Walks the ledger from its first entry and checks every link (ADR-014): the sequence has no gap, each entry points at
 * the hash of the one before, each hash matches the entry's fields, and each payload that is still present matches the
 * digest the chain committed to. The first broken link is reported; an erased payload is counted, not a break.
 */
public final class AudChainVerifier {

    /**
     * @param intact          whether every checked link held
     * @param entriesChecked  how many entries were checked
     * @param payloadsErased  how many of them had their payload erased (the chain still holds for them)
     * @param firstBrokenSeq  the first entry that failed, or null when intact
     * @param reason          why it failed, or null when intact
     * @param headSeq         the last entry checked (0 for an empty ledger)
     * @param headHash        its hash ({@link AudChain#GENESIS_HASH} for an empty ledger)
     */
    public record Verdict(boolean intact, long entriesChecked, long payloadsErased, Long firstBrokenSeq, String reason,
                          long headSeq, String headHash) {
    }

    private long expectedSeq = 1;
    private String expectedPrevHash = AudChain.GENESIS_HASH;
    private long checked;
    private long erased;
    private Long brokenSeq;
    private String reason;

    /** Checks the next entry, in sequence order; after the first break the rest are ignored. */
    public void accept(AudRecord record) {
        if (brokenSeq != null) {
            return;
        }
        AudEntry entry = record.entry();
        checked++;
        if (entry.seq() != expectedSeq) {
            fail(entry.seq(), "expected entry " + expectedSeq + " here: an entry is missing or out of order");
        } else if (!entry.prevHash().equals(expectedPrevHash)) {
            fail(entry.seq(), "does not point at the previous entry's hash");
        } else if (!entry.entryHash().equals(AudChain.entryHash(entry))) {
            fail(entry.seq(), "its fields no longer match its hash");
        } else if (record.payloadJson() == null) {
            erased++;
        } else if (!entry.payloadSha256().equals(AudChain.sha256Hex(record.payloadJson()))) {
            fail(entry.seq(), "its payload no longer matches the digest in the chain");
        }
        if (brokenSeq == null) {
            expectedSeq = entry.seq() + 1;
            expectedPrevHash = entry.entryHash();
        }
    }

    private void fail(long seq, String why) {
        brokenSeq = seq;
        reason = "Entry " + seq + " " + why;
    }

    public Verdict verdict() {
        return new Verdict(brokenSeq == null, checked, erased, brokenSeq, reason, expectedSeq - 1, expectedPrevHash);
    }
}
