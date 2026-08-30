package com.techeazyhq.cm.ntf.ifc.rest;

import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.techeazyhq.cm.ntf.app.OtpSendCmd;
import com.techeazyhq.cm.ntf.app.OtpSendCmdHandler;

import reactor.core.publisher.Mono;

/**
 * Functional-endpoint handler (plan §10's preference) for {@code POST /internal/otp/send} —
 * network-restricted to {@code cm-idn-otp-spi} (plan §9a), same convention as this platform's
 * other {@code /internal/**} endpoints.
 */
public class OtpSendHandler {

    private final OtpSendCmdHandler otpSendCmdHandler;

    public OtpSendHandler(OtpSendCmdHandler otpSendCmdHandler) {
        this.otpSendCmdHandler = otpSendCmdHandler;
    }

    public Mono<ServerResponse> send(ServerRequest request) {
        return request.bodyToMono(OtpSendReqDto.class)
                .flatMap(body -> otpSendCmdHandler.handle(new OtpSendCmd(body.channel(), body.destination(), body.code())))
                .then(ServerResponse.ok().build());
    }
}
