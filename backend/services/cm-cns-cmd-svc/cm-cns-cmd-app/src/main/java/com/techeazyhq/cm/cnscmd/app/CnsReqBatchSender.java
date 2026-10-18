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

import org.springframework.transaction.annotation.Transactional;

import com.techeazyhq.cm.cnscmd.dom.CnsReq;
import com.techeazyhq.cm.cnscmd.dom.CnsReqBatch;
import com.techeazyhq.cm.cnscmd.dom.CnsReqBatchRepoPort;
import com.techeazyhq.cm.cnscmd.dom.CnsReqRepoPort;

import reactor.core.publisher.Mono;

/**
 * Turns one row of a batch into its consent request. The request (with its creation event in the outbox) and the
 * row's outcome commit together, so a row is never sent twice nor marked sent without its request.
 */
public class CnsReqBatchSender {

    private final CnsReqRepoPort cnsReqRepo;
    private final CnsReqBatchRepoPort batchRepo;

    public CnsReqBatchSender(CnsReqRepoPort cnsReqRepo, CnsReqBatchRepoPort batchRepo) {
        this.cnsReqRepo = cnsReqRepo;
        this.batchRepo = batchRepo;
    }

    @Transactional
    public Mono<CnsReqBatch.Row> send(CnsReqBatch batch, CnsReqBatch.Row row) {
        return Mono.defer(() -> {
            CnsReq cnsReq = batch.template().requestFor(batch.dfId(), row.recipient());
            return cnsReqRepo.save(cnsReq)
                    .flatMap(saved -> {
                        CnsReqBatch.Row sent = row.sent(saved.id());
                        return batchRepo.saveRow(batch.id(), sent).thenReturn(sent);
                    });
        });
    }
}
