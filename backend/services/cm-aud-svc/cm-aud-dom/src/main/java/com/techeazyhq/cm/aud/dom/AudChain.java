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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;

/**
 * The hash-chain rules of the audit ledger (ADR-014), in one place so that recording and verifying cannot drift apart.
 *
 * <p>An entry's hash is the SHA-256, as lower-case hex, of these lines joined by {@code \n}:
 * <pre>
 * cm-aud-v1
 * seq
 * evtId
 * evtType
 * occurredAt      (ISO-8601, microseconds)
 * recordedAt      (ISO-8601, microseconds)
 * cnsReqId        (empty when absent)
 * cnsArtId        (empty when absent)
 * dfId            (empty when absent)
 * payloadSha256
 * prevHash        (64 zeros for the first entry)
 * </pre>
 * Anyone with the entries can recompute it; nothing in it is secret or personal.
 */
public final class AudChain {

    public static final String GENESIS_HASH = "0".repeat(64);

    private static final String VERSION = "cm-aud-v1";

    private AudChain() {
    }

    /** The entry that follows {@code (prevSeq, prevHash)} for this draft, recorded at {@code recordedAt}. */
    public static AudEntry link(long prevSeq, String prevHash, AudDraft draft, Instant recordedAt) {
        Instant recorded = recordedAt.truncatedTo(ChronoUnit.MICROS);
        long seq = prevSeq + 1;
        String payloadSha256 = sha256Hex(draft.payloadJson());
        String entryHash = entryHash(seq, draft.evtId().toString(), draft.evtType(), draft.occurredAt(), recorded,
                draft.cnsReqId(), draft.cnsArtId(), draft.dfId(), payloadSha256, prevHash);
        return new AudEntry(seq, draft.evtId(), draft.evtType(), draft.occurredAt(), recorded, draft.cnsReqId(),
                draft.cnsArtId(), draft.dfId(), payloadSha256, prevHash, entryHash);
    }

    /** Recomputes an entry's hash from its fields, as a verifier would. */
    public static String entryHash(AudEntry entry) {
        return entryHash(entry.seq(), entry.evtId().toString(), entry.evtType(), entry.occurredAt(), entry.recordedAt(),
                entry.cnsReqId(), entry.cnsArtId(), entry.dfId(), entry.payloadSha256(), entry.prevHash());
    }

    private static String entryHash(long seq, String evtId, String evtType, Instant occurredAt, Instant recordedAt,
                                    String cnsReqId, String cnsArtId, String dfId, String payloadSha256, String prevHash) {
        return sha256Hex(String.join("\n", VERSION, Long.toString(seq), evtId, evtType,
                occurredAt.truncatedTo(ChronoUnit.MICROS).toString(), recordedAt.truncatedTo(ChronoUnit.MICROS).toString(),
                orEmpty(cnsReqId), orEmpty(cnsArtId), orEmpty(dfId), payloadSha256, prevHash));
    }

    public static String sha256Hex(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }
}
