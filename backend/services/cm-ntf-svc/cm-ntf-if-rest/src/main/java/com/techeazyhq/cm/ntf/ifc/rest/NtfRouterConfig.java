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
