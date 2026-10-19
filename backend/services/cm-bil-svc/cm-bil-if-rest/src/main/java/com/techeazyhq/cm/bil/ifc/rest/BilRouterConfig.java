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

package com.techeazyhq.cm.bil.ifc.rest;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.techeazyhq.cm.bil.app.AssignSubPlnCmdHandler;
import com.techeazyhq.cm.bil.app.BilOverviewQryHandler;
import com.techeazyhq.cm.bil.app.PlnChgReqHandler;
import com.techeazyhq.cm.bil.app.GetDfSubStatusQryHandler;
import com.techeazyhq.cm.bil.app.GetUsgQryHandler;
import com.techeazyhq.cm.bil.app.ListSubPlnsQryHandler;
import com.techeazyhq.cm.bil.app.SetDfSubStsCmdHandler;
import com.techeazyhq.cm.bil.app.SubEligQryHandler;
import com.techeazyhq.cm.bil.app.UsgReservationHandler;

@Configuration
public class BilRouterConfig {

    @Bean
    public RouterFunction<ServerResponse> bilRoutes(AssignSubPlnCmdHandler assignSubPlnHandler,
                                                      SetDfSubStsCmdHandler setDfSubStsHandler,
                                                      GetUsgQryHandler getUsgHandler,
                                                      SubEligQryHandler subEligHandler,
                                                      ListSubPlnsQryHandler listSubPlnsHandler,
                                                      GetDfSubStatusQryHandler getDfSubStatusHandler,
                                                      UsgReservationHandler usgReservationHandler,
                                                      BilOverviewQryHandler overviewHandler,
                                                      PlnChgReqHandler plnChgReqHandler) {
        BilHandler handler = new BilHandler(assignSubPlnHandler, setDfSubStsHandler, getUsgHandler, subEligHandler,
                listSubPlnsHandler, getDfSubStatusHandler, usgReservationHandler);
        BilAccountHttpHandler account = new BilAccountHttpHandler(getDfSubStatusHandler, overviewHandler, plnChgReqHandler);
        return RouterFunctions.route()
                .GET("/api/bil/me/subscription", account::mySubscription)
                .GET("/api/bil/me/plan-change-requests", account::myRequests)
                .POST("/api/bil/me/plan-change-requests", account::requestChange)
                .POST("/api/bil/me/plan-change-requests/{id}/withdraw", account::withdraw)
                .GET("/api/bil/overview", account::overview)
                .GET("/api/bil/plan-change-requests", account::list)
                .POST("/api/bil/plan-change-requests/{id}/approve", account::approve)
                .POST("/api/bil/plan-change-requests/{id}/decline", account::decline)
                .GET("/api/bil/sub-plns", handler::listSubPlns)
                .PUT("/api/bil/dfs/{id}/sub-pln", handler::assignSubPln)
                .PUT("/api/bil/dfs/{id}/sub-status", handler::setSubSts)
                .GET("/api/bil/dfs/{id}/usage", handler::usage)
                .GET("/api/bil/dfs/{id}/sub-status", handler::subStatus)
                .GET("/internal/dfs/{id}/sub-eligibility", handler::eligibility)
                .GET("/internal/sub-plns/{name}", handler::subPln)
                .POST("/internal/dfs/{id}/usage/reservations", handler::reserveUsage)
                .POST("/internal/dfs/{id}/usage/releases", handler::releaseUsage)
                .build();
    }
}
