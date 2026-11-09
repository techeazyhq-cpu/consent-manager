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

package com.techeazyhq.cm.aud.ifc.rest;

import com.techeazyhq.cm.aud.app.AudAnchoringService;
import com.techeazyhq.cm.aud.app.AudLedgerService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

@Configuration
public class AudRouterConfig {

    @Bean
    public RouterFunction<ServerResponse> audRoutes(AudLedgerService ledger, AudAnchoringService anchoring,
                                                  com.techeazyhq.cm.aud.app.PurgeDfRecordsHandler purgeDfRecords,
                                                  com.techeazyhq.cm.aud.dom.AnomalyPort anomalies) {
        AudHandler handler = new AudHandler(ledger, anchoring);
        AudActivityHttpHandler activity = new AudActivityHttpHandler(ledger);
        DfPurgeHttpHandler dfPurge = new DfPurgeHttpHandler(purgeDfRecords);
        DfExportHttpHandler dfExport = new DfExportHttpHandler(ledger);
        AnomalyHttpHandler anomaly = new AnomalyHttpHandler(anomalies, java.time.Clock.systemUTC());
        return RouterFunctions.route()
                .POST("/internal/dfs/{id}/purge", dfPurge::purge)
                .GET("/internal/dfs/{id}/export", dfExport::export)
                .GET("/api/aud/activity", activity::activity)
                .GET("/api/aud/anomalies", anomaly::list)
                .POST("/api/aud/anomalies/{id}/acknowledge", anomaly::acknowledge)
                .GET("/api/aud/entries", handler::entries)
                .GET("/api/aud/chain/head", handler::head)
                .GET("/api/aud/chain/verify", handler::verify)
                .GET("/api/aud/entries/{seq}/proof", handler::proof)
                .GET("/api/aud/checkpoints", handler::checkpoints)
                .build();
    }
}
