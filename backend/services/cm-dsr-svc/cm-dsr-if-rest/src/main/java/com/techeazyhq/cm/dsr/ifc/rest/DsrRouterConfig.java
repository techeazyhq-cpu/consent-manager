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

package com.techeazyhq.cm.dsr.ifc.rest;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.techeazyhq.cm.dsr.app.DpErasureHandler;
import com.techeazyhq.cm.dsr.app.DpReqHandler;

@Configuration
public class DsrRouterConfig {

    @Bean
    public RouterFunction<ServerResponse> dsrRoutes(DpReqHandler requests, DpErasureHandler erasures, Clock clock) {
        DsrHttpHandler handler = new DsrHttpHandler(requests, erasures, clock);
        return RouterFunctions.route()
                .GET("/api/dsr/me/requests", handler::mine)
                .POST("/api/dsr/me/requests", handler::raise)
                .POST("/api/dsr/me/requests/{id}/withdraw", handler::withdraw)
                .GET("/api/dsr/me/account/close", handler::myErasure)
                .POST("/api/dsr/me/account/close", handler::closeAccount)
                .GET("/api/dsr/erasures", handler::erasures)
                .POST("/api/dsr/erasures/{id}/retry", handler::retryErasure)
                .GET("/api/dsr/df/requests", handler::forFiduciary)
                .GET("/api/dsr/df/requests/{id}", handler::oneForFiduciary)
                .POST("/api/dsr/df/requests/{id}/acknowledge", handler::acknowledge)
                .POST("/api/dsr/df/requests/{id}/resolve", handler::resolve)
                .POST("/api/dsr/df/requests/{id}/refuse", handler::refuse)
                .GET("/api/dsr/grievances", handler::grievances)
                .POST("/api/dsr/grievances/{id}/acknowledge", handler::acknowledge)
                .POST("/api/dsr/grievances/{id}/resolve", handler::resolve)
                .POST("/api/dsr/grievances/{id}/refuse", handler::refuse)
                .build();
    }
}
