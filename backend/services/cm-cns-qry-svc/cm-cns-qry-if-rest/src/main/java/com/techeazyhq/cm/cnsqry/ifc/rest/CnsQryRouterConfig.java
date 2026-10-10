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

package com.techeazyhq.cm.cnsqry.ifc.rest;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.techeazyhq.cm.cnsqry.app.DpDashboardQryHandler;
import com.techeazyhq.cm.cnsqry.app.GetCnsArtQryHandler;
import com.techeazyhq.cm.cnsqry.app.GetCnsHstQryHandler;
import com.techeazyhq.cm.cnsqry.app.InternalStatusQryHandler;

@Configuration
public class CnsQryRouterConfig {

    @Bean
    public RouterFunction<ServerResponse> cnsQryRoutes(GetCnsArtQryHandler getCnsArtHandler,
                                                         GetCnsHstQryHandler getCnsHstHandler,
                                                         DpDashboardQryHandler dpDashboardHandler,
                                                         InternalStatusQryHandler internalStatusHandler) {
        CnsQryHandler handler = new CnsQryHandler(getCnsArtHandler, getCnsHstHandler, dpDashboardHandler, internalStatusHandler);
        return RouterFunctions.route()
                .GET("/api/cns-qry/cns-arts", handler::listCnsArts)
                .GET("/api/cns-qry/cns-arts/{id}", handler::getCnsArt)
                .GET("/api/cns-qry/cns-arts/{id}/history", handler::getCnsHst)
                .GET("/api/cns-qry/cns-arts/{id}/verify", handler::verify)
                .GET("/api/cns-qry/dp-dashboard", handler::dpDashboard)
                .GET("/internal/cns-arts/{id}/status", handler::internalStatus)
                .build();
    }
}
