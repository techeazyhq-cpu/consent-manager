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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AudChainTest {

    private static final Instant T0 = Instant.parse("2026-10-10T12:00:00.123456789Z");

    private static AudDraft draft(int n) {
        return new AudDraft(UUID.nameUUIDFromBytes(("evt-" + n).getBytes()), "CnsGrantedEvt", T0.plusSeconds(n),
                "req-" + n, "art-" + n, "df-1", "dp-1", "{\"n\":" + n + "}");
    }

    private static List<AudRecord> chainOf(int length) {
        List<AudRecord> records = new ArrayList<>();
        long seq = 0;
        String hash = AudChain.GENESIS_HASH;
        for (int n = 1; n <= length; n++) {
            AudDraft draft = draft(n);
            AudEntry entry = AudChain.link(seq, hash, draft, T0.plusSeconds(100 + n));
            records.add(new AudRecord(entry, draft.payloadJson()));
            seq = entry.seq();
            hash = entry.entryHash();
        }
        return records;
    }

    private static AudChainVerifier.Verdict verify(List<AudRecord> records) {
        AudChainVerifier verifier = new AudChainVerifier();
        records.forEach(verifier::accept);
        return verifier.verdict();
    }

    @Test
    void eachEntryPointsAtThePreviousOneAndKeepsOnlyMicroseconds() {
        List<AudRecord> chain = chainOf(3);

        assertEquals(AudChain.GENESIS_HASH, chain.get(0).entry().prevHash());
        assertEquals(chain.get(0).entry().entryHash(), chain.get(1).entry().prevHash());
        assertEquals(3, chain.get(2).entry().seq());
        assertEquals(Instant.parse("2026-10-10T12:00:01.123456Z"), chain.get(0).entry().occurredAt());
        assertEquals(AudChain.sha256Hex("{\"n\":1}"), chain.get(0).entry().payloadSha256());
    }

    @Test
    void anUntouchedChainVerifies() {
        AudChainVerifier.Verdict verdict = verify(chainOf(5));

        assertTrue(verdict.intact());
        assertEquals(5, verdict.entriesChecked());
        assertEquals(5, verdict.headSeq());
        assertNull(verdict.firstBrokenSeq());
    }

    @Test
    void anEmptyLedgerVerifiesAtGenesis() {
        AudChainVerifier.Verdict verdict = verify(List.of());

        assertTrue(verdict.intact());
        assertEquals(0, verdict.headSeq());
        assertEquals(AudChain.GENESIS_HASH, verdict.headHash());
    }

    @Test
    void changingAnyRecordedFieldIsDetectedAtThatEntry() {
        List<AudRecord> chain = chainOf(4);
        AudEntry original = chain.get(1).entry();
        AudEntry edited = new AudEntry(original.seq(), original.evtId(), "CnsDeniedEvt", original.occurredAt(),
                original.recordedAt(), original.cnsReqId(), original.cnsArtId(), original.dfId(), original.payloadSha256(),
                original.prevHash(), original.entryHash());
        chain.set(1, new AudRecord(edited, chain.get(1).payloadJson()));

        AudChainVerifier.Verdict verdict = verify(chain);

        assertFalse(verdict.intact());
        assertEquals(2L, verdict.firstBrokenSeq());
        assertTrue(verdict.reason().contains("no longer match its hash"));
    }

    @Test
    void rehashingAnEditedEntryStillBreaksTheNextLink() {
        List<AudRecord> chain = chainOf(4);
        AudEntry original = chain.get(1).entry();
        AudEntry forged = AudChain.link(1, original.prevHash(),
                new AudDraft(original.evtId(), "CnsDeniedEvt", original.occurredAt(), original.cnsReqId(),
                        original.cnsArtId(), original.dfId(), null, "{\"n\":2}"), original.recordedAt());
        chain.set(1, new AudRecord(forged, "{\"n\":2}"));

        AudChainVerifier.Verdict verdict = verify(chain);

        assertNotEquals(original.entryHash(), forged.entryHash());
        assertEquals(3L, verdict.firstBrokenSeq());
        assertTrue(verdict.reason().contains("previous entry's hash"));
    }

    @Test
    void aRemovedEntryIsDetected() {
        List<AudRecord> chain = chainOf(4);
        chain.remove(2);

        assertEquals(4L, verify(chain).firstBrokenSeq());
    }

    @Test
    void aChangedPayloadIsDetectedButAnErasedOneIsNot() {
        List<AudRecord> changed = chainOf(3);
        changed.set(0, new AudRecord(changed.get(0).entry(), "{\"n\":99}"));
        List<AudRecord> erased = chainOf(3);
        erased.set(0, new AudRecord(erased.get(0).entry(), null));

        assertEquals(1L, verify(changed).firstBrokenSeq());
        AudChainVerifier.Verdict afterErasure = verify(erased);
        assertTrue(afterErasure.intact());
        assertEquals(1, afterErasure.payloadsErased());
    }
}
