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

package com.techeazyhq.cm.ntf.ifc.rest;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.techeazyhq.cm.ntf.app.OtpSendCmdHandler;
import com.techeazyhq.cm.ntf.app.PushDvcCmdHandler;

@Configuration
public class NtfRouterConfig {

    @Bean
    public RouterFunction<ServerResponse> ntfRoutes(OtpSendCmdHandler otpSendCmdHandler, PushDvcCmdHandler pushDvcCmdHandler) {
        OtpSendHandler otpSendHandler = new OtpSendHandler(otpSendCmdHandler);
        PushDvcHandler pushDvcHandler = new PushDvcHandler(pushDvcCmdHandler);
        return RouterFunctions.route()
                .POST("/internal/otp/send", otpSendHandler::send)
                .GET("/api/ntf/push-dvcs", pushDvcHandler::list)
                .POST("/api/ntf/push-dvcs", pushDvcHandler::register)
                .DELETE("/api/ntf/push-dvcs/{id}", pushDvcHandler::remove)
                .build();
    }
}
