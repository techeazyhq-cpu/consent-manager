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

package com.techeazyhq.cm.cnscmd.dom;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Many consent requests from one template, to a list of people (ADR-036). The list is checked when the batch is made:
 * each row is valid, invalid (with why) or a repeat of an earlier row, and only valid rows are ever sent, each as an
 * ordinary consent request. Sending happens afterwards, in chunks, so the batch reports its progress row by row.
 */
public final class CnsReqBatch {

    /** The most rows one batch takes; a longer list is split into several batches. */
    public static final int MAX_ROWS = 10_000;

    public enum Source { API, CSV, XLSX }

    public enum Sts { QUEUED, SENDING, DONE }

    public enum Outcome {
        /** Valid, waiting to be sent. */
        PENDING,
        SENT,
        INVALID,
        DUPLICATE,
        /** Valid, but creating its request failed. */
        FAILED
    }

    /** One line of the list: where it was (1-based), what it said, and what became of it. */
    public record Row(int rowNo, String contact, String ref, ExtDpRefTyp typ, String val, Outcome outcome,
                      String cnsReqId, String problem) {

        public Row sent(String newCnsReqId) {
            return new Row(rowNo, contact, ref, typ, val, Outcome.SENT, newCnsReqId, null);
        }

        public Row failed(String why) {
            return new Row(rowNo, contact, ref, typ, val, Outcome.FAILED, null, why);
        }

        public Recipient recipient() {
            return new Recipient(typ, val);
        }
    }

    /** A row as it came in: the contact, an optional reference of the fiduciary's own, and optionally its type. */
    public record Input(String contact, String ref, ExtDpRefTyp typ) {
    }

    private final String id;
    private final String dfId;
    private final CnsReqTemplate template;
    private final Source source;
    private final String fileName;
    private final String createdBy;
    private final Instant createdAt;
    private final int total;
    private final int valid;
    private final int invalid;
    private final int duplicate;
    private int sent;
    private int failed;
    private Sts sts;
    private Instant finishedAt;

    private CnsReqBatch(String id, String dfId, CnsReqTemplate template, Source source, String fileName,
                        String createdBy, Instant createdAt, int total, int valid, int invalid, int duplicate, int sent,
                        int failed, Sts sts, Instant finishedAt) {
        this.id = id;
        this.dfId = dfId;
        this.template = template;
        this.source = source;
        this.fileName = fileName;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.total = total;
        this.valid = valid;
        this.invalid = invalid;
        this.duplicate = duplicate;
        this.sent = sent;
        this.failed = failed;
        this.sts = sts;
        this.finishedAt = finishedAt;
    }

    /** The checked list and the batch it would make, before anything is stored or sent. */
    public record Plan(CnsReqBatch batch, List<Row> rows) {

        public List<Row> problems() {
            return rows.stream().filter(r -> r.outcome() == Outcome.INVALID || r.outcome() == Outcome.DUPLICATE).toList();
        }
    }

    /**
     * Checks every row: an unreadable contact is invalid; the same person twice is sent once, the later rows marked as
     * duplicates. Throws if the list is empty or longer than {@link #MAX_ROWS}.
     */
    public static Plan plan(String dfId, CnsReqTemplate template, Source source, String fileName, String createdBy,
                            List<Input> inputs) {
        return plan(dfId, template, source, fileName, createdBy, inputs, ContactPolicy.ANYONE);
    }

    /** As above, skipping as invalid any contact {@code policy} refuses (in the sandbox, anything but test contacts). */
    public static Plan plan(String dfId, CnsReqTemplate template, Source source, String fileName, String createdBy,
                            List<Input> inputs, ContactPolicy policy) {
        if (inputs == null || inputs.isEmpty()) {
            throw new IllegalArgumentException("the list has no rows");
        }
        if (inputs.size() > MAX_ROWS) {
            throw new IllegalArgumentException("the list has " + inputs.size() + " rows; a batch takes at most "
                    + MAX_ROWS + ", so split it");
        }
        List<Row> rows = new ArrayList<>(inputs.size());
        Set<Recipient> seen = new HashSet<>();
        int valid = 0;
        int invalid = 0;
        int duplicate = 0;
        for (int i = 0; i < inputs.size(); i++) {
            Input in = inputs.get(i);
            String ref = in.ref() == null || in.ref().isBlank() ? null : truncate(in.ref().trim(), 128);
            String contact = in.contact() == null ? "" : truncate(in.contact().trim(), 255);
            Recipient.Parsed parsed = Recipient.parse(contact, in.typ());
            if (parsed instanceof Recipient.Ok ok && policy.refusal(ok.recipient()).isPresent()) {
                parsed = new Recipient.Problem(policy.refusal(ok.recipient()).get());
            }
            switch (parsed) {
                case Recipient.Ok ok when !seen.add(ok.recipient()) -> {
                    duplicate++;
                    rows.add(new Row(i + 1, contact, ref, ok.recipient().typ(), ok.recipient().val(),
                            Outcome.DUPLICATE, null, "Already in this list"));
                }
                case Recipient.Ok ok -> {
                    valid++;
                    rows.add(new Row(i + 1, contact, ref, ok.recipient().typ(), ok.recipient().val(),
                            Outcome.PENDING, null, null));
                }
                case Recipient.Problem problem -> {
                    invalid++;
                    rows.add(new Row(i + 1, contact, ref, null, null, Outcome.INVALID, null, problem.reason()));
                }
            }
        }
        CnsReqBatch batch = new CnsReqBatch(UUID.randomUUID().toString(), dfId, template, source, fileName, createdBy,
                Instant.now(), inputs.size(), valid, invalid, duplicate, 0, 0,
                valid == 0 ? Sts.DONE : Sts.QUEUED, valid == 0 ? Instant.now() : null);
        return new Plan(batch, List.copyOf(rows));
    }

    public static CnsReqBatch rehydrate(String id, String dfId, CnsReqTemplate template, Source source, String fileName,
                                        String createdBy, Instant createdAt, int total, int valid, int invalid,
                                        int duplicate, int sent, int failed, Sts sts, Instant finishedAt) {
        return new CnsReqBatch(id, dfId, template, source, fileName, createdBy, createdAt, total, valid, invalid,
                duplicate, sent, failed, sts, finishedAt);
    }

    /** Records how a chunk of rows went. */
    public void progressed(int newlySent, int newlyFailed) {
        sent += newlySent;
        failed += newlyFailed;
        sts = Sts.SENDING;
    }

    /** Every valid row is sent or failed; how many reserved requests were not used. */
    public int finish() {
        sts = Sts.DONE;
        finishedAt = Instant.now();
        return valid - sent;
    }

    public boolean allRowsDone() {
        return sent + failed >= valid;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    public String id() {
        return id;
    }

    public String dfId() {
        return dfId;
    }

    public CnsReqTemplate template() {
        return template;
    }

    public Source source() {
        return source;
    }

    public String fileName() {
        return fileName;
    }

    public String createdBy() {
        return createdBy;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public int total() {
        return total;
    }

    public int valid() {
        return valid;
    }

    public int invalid() {
        return invalid;
    }

    public int duplicate() {
        return duplicate;
    }

    public int sent() {
        return sent;
    }

    public int failed() {
        return failed;
    }

    public Sts sts() {
        return sts;
    }

    public Instant finishedAt() {
        return finishedAt;
    }
}
