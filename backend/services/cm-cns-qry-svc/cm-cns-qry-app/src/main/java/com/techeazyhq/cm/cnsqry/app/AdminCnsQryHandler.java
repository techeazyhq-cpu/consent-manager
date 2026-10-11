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

package com.techeazyhq.cm.cnsqry.app;

import com.techeazyhq.cm.cnsqry.dom.CnsArtRdMdlRepoPort;
import com.techeazyhq.cm.cnsqry.dom.CnsLcySts;
import com.techeazyhq.cm.cnsqry.dom.CnsStats;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Backs CM Admin's tenant detail: a fiduciary's consent counts by status, and its requests page by page with the data
 * principal masked. Callers are platform staff only; the interface layer checks that.
 */
public class AdminCnsQryHandler {

    public static final int MAX_PAGE_SIZE = 100;

    private final CnsArtRdMdlRepoPort rdMdlRepo;

    public AdminCnsQryHandler(CnsArtRdMdlRepoPort rdMdlRepo) {
        this.rdMdlRepo = rdMdlRepo;
    }

    public Mono<CnsStats> stats(String dfId) {
        return rdMdlRepo.countByStsForDfId(dfId).map(counts -> CnsStats.of(dfId, counts));
    }

    /** @param sts null for every status; {@code limit} is capped at {@link #MAX_PAGE_SIZE} */
    public Flux<AdminCnsReqView> requests(String dfId, CnsLcySts sts, int limit, long offset) {
        return rdMdlRepo.pageByDfId(dfId, sts, Math.min(Math.max(limit, 1), MAX_PAGE_SIZE), Math.max(offset, 0))
                .map(AdminCnsReqView::of);
    }
}
