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

import java.util.List;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Where batches and their rows are kept (ADR-036). */
public interface CnsReqBatchRepoPort {

    /** Stores a new batch with all its rows. */
    Mono<CnsReqBatch> create(CnsReqBatch batch, List<CnsReqBatch.Row> rows);

    /** Saves the batch's counts and status. */
    Mono<CnsReqBatch> save(CnsReqBatch batch);

    Mono<CnsReqBatch> findById(String id);

    /** The fiduciary's batches, newest first. */
    Flux<CnsReqBatch> findByDfId(String dfId, int limit, int offset);

    /** Batches with rows still to send, oldest first. */
    Flux<CnsReqBatch> findUnfinished(int limit);

    /**
     * Takes up to {@code limit} of the batch's pending rows to send, so no other worker takes them too; taken rows
     * stay pending in the store until {@link #saveRow} records their outcome.
     */
    Flux<CnsReqBatch.Row> claimPending(String batchId, int limit);

    Mono<Void> saveRow(String batchId, CnsReqBatch.Row row);

    /** The batch's rows in list order, optionally only those with one outcome. */
    Flux<CnsReqBatch.Row> rows(String batchId, CnsReqBatch.Outcome outcome, int limit, int offset);
}
