package com.techeazyhq.cm.ntf.ifc.rest;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.techeazyhq.cm.ntf.app.OtpSendCmdHandler;

@Configuration
public class NtfRouterConfig {

    @Bean
    public RouterFunction<ServerResponse> ntfRoutes(OtpSendCmdHandler otpSendCmdHandler) {
        OtpSendHandler otpSendHandler = new OtpSendHandler(otpSendCmdHandler);
        return RouterFunctions.route()
                .POST("/internal/otp/send", otpSendHandler::send)
                .build();
    }
}
