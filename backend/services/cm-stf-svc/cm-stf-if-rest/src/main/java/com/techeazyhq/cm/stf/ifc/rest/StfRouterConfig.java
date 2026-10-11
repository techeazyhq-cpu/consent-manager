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

package com.techeazyhq.cm.stf.ifc.rest;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.techeazyhq.cm.stf.app.CreateOperatorCmdHandler;
import com.techeazyhq.cm.stf.app.ListStfQryHandler;
import com.techeazyhq.cm.stf.app.MyAcctHandler;
import com.techeazyhq.cm.stf.app.SetStfEnabledCmdHandler;

@Configuration
public class StfRouterConfig {

    @Bean
    public RouterFunction<ServerResponse> stfRoutes(CreateOperatorCmdHandler createOperatorHandler,
                                                    SetStfEnabledCmdHandler setEnabledHandler,
                                                    ListStfQryHandler listHandler,
                                                    MyAcctHandler myAcctHandler) {
        StfHandler handler = new StfHandler(createOperatorHandler, setEnabledHandler, listHandler, myAcctHandler);
        return RouterFunctions.route()
                .GET("/api/stf/me", handler::me)
                .GET("/api/stf/me/sessions", handler::mySessions)
                .POST("/api/stf/me/sign-out-everywhere", handler::signOutEverywhere)
                .GET("/api/stf/staff", handler::listStaff)
                .POST("/api/stf/operators", handler::createOperator)
                .PUT("/api/stf/staff/{id}/enabled", handler::setEnabled)
                .build();
    }
}
