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
import com.techeazyhq.cm.cnsqry.dom.DpDashboardRow;

import reactor.core.publisher.Flux;

/** Backs the DP tile dashboard (plan §8, §17 — the user's own request): one summary row per DF. */
public class DpDashboardQryHandler {

    private final CnsArtRdMdlRepoPort rdMdlRepo;

    public DpDashboardQryHandler(CnsArtRdMdlRepoPort rdMdlRepo) {
        this.rdMdlRepo = rdMdlRepo;
    }

    public Flux<DpDashboardRow> handle(String dpId) {
        return rdMdlRepo.dashboardFor(dpId);
    }
}
