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

import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.techeazyhq.cm.ntf.app.OtpSendCmd;
import com.techeazyhq.cm.ntf.app.OtpSendCmdHandler;
import com.techeazyhq.cm.ntf.dom.NtfDeliveryRejectedException;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Mono;

import java.util.Map;

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
                // no body, or a body without a channel, a destination or a code, is the caller's mistake (400), not ours (500)
                .switchIfEmpty(Mono.error(() -> invalid()))
                .flatMap(body -> body.channel() == null || blank(body.destination()) || blank(body.code())
                        ? Mono.<Void>error(invalid())
                        : otpSendCmdHandler.handle(new OtpSendCmd(body.channel(), body.destination(), body.code())))
                .then(ServerResponse.ok().build())
                // e.g. an invalid phone number: the caller has to ask the user again, retrying won't help
                .onErrorResume(NtfDeliveryRejectedException.class, err ->
                        ServerResponse.unprocessableEntity().bodyValue(Map.of("code", err.code())))
                // the limit on what this service sends (ADR-080): the sign-in is told to wait, and says the same to the person
                .onErrorResume(com.techeazyhq.cm.webkit.DomRuleViolation.class, err -> err.httpStatus() == 429
                        ? ServerResponse.status(429).header("Retry-After", "600").bodyValue(Map.of("code", err.problemType()))
                        : Mono.error(err));
    }

    private static DomRuleViolation invalid() {
        return new DomRuleViolation(400, "otp-send-invalid", "A channel, a destination and a code are needed");
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
