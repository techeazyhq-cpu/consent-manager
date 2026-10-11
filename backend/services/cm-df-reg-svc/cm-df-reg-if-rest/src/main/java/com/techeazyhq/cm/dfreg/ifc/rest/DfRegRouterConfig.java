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

package com.techeazyhq.cm.dfreg.ifc.rest;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.techeazyhq.cm.dfreg.app.ActivateDfCmdHandler;
import com.techeazyhq.cm.dfreg.app.GetDfActiveQryHandler;
import com.techeazyhq.cm.dfreg.app.GetDfLogoQryHandler;
import com.techeazyhq.cm.dfreg.app.GetDfQryHandler;
import com.techeazyhq.cm.dfreg.app.ListDfsQryHandler;
import com.techeazyhq.cm.dfreg.app.RegisterDfCmdHandler;
import com.techeazyhq.cm.dfreg.app.UploadDfLogoCmdHandler;

@Configuration
public class DfRegRouterConfig {

    @Bean
    public RouterFunction<ServerResponse> dfRegRoutes(RegisterDfCmdHandler registerHandler,
                                                        ActivateDfCmdHandler activateHandler,
                                                        GetDfActiveQryHandler getActiveHandler,
                                                        ListDfsQryHandler listHandler,
                                                        GetDfQryHandler getDfHandler,
                                                        UploadDfLogoCmdHandler uploadLogoHandler,
                                                        GetDfLogoQryHandler getLogoHandler) {
        DfHandler dfHandler = new DfHandler(registerHandler, activateHandler, getActiveHandler, listHandler, getDfHandler,
                uploadLogoHandler, getLogoHandler);
        return RouterFunctions.route()
                .GET("/api/df-reg/dfs", dfHandler::list)
                .POST("/api/df-reg/dfs", dfHandler::register)
                .POST("/api/df-reg/dfs/{id}/activate", dfHandler::activate)
                .GET("/api/df-reg/dfs/{id}", dfHandler::getOne)
                .GET("/api/df-reg/dfs/{id}/profile", dfHandler::profile)
                .PUT("/api/df-reg/dfs/{id}/logo", dfHandler::uploadLogo)
                .GET("/api/df-reg/dfs/{id}/logo", dfHandler::logo)
                .GET("/internal/dfs/{id}/active", dfHandler::isActive)
                .build();
    }
}
