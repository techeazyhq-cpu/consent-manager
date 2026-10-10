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

package com.techeazyhq.cm.dfreg.app;

import com.techeazyhq.cm.dfreg.dom.Df;
import com.techeazyhq.cm.dfreg.dom.DfRepoPort;

import reactor.core.publisher.Mono;

/** Backs {@code GET /api/df-reg/dfs/{id}} — a non-sensitive name lookup any authenticated
 * role can call (e.g. mf-dp-portal resolving a DF's display name for its tile dashboard). */
public class GetDfQryHandler {

    private final DfRepoPort dfRepo;

    public GetDfQryHandler(DfRepoPort dfRepo) {
        this.dfRepo = dfRepo;
    }

    public Mono<Df> handle(String id) {
        return dfRepo.findById(id);
    }
}
