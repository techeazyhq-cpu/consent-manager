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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.reactive.function.server.MockServerRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.server.HandlerStrategies;
import org.springframework.web.reactive.function.server.ServerRequest;

import com.techeazyhq.cm.ntf.app.OtpSendCmd;
import com.techeazyhq.cm.ntf.app.OtpSendCmdHandler;
import com.techeazyhq.cm.ntf.dom.NtfChnl;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** A request to send a sign-in code that is missing something is the caller's mistake (400), and is never sent. */
class OtpSendHandlerTest {

    private final AtomicInteger sent = new AtomicInteger();
    private final OtpSendHandler handler = new OtpSendHandler(new OtpSendCmdHandler(null) {
        @Override
        public Mono<Void> handle(OtpSendCmd cmd) {
            sent.incrementAndGet();
            return Mono.empty();
        }
    });

    private void rejected(ServerRequest request) {
        StepVerifier.create(handler.send(request))
                .expectErrorSatisfies(e -> {
                    DomRuleViolation v = (DomRuleViolation) e;
                    assertEquals(400, v.httpStatus());
                    assertEquals("otp-send-invalid", v.problemType());
                })
                .verify();
        assertEquals(0, sent.get(), "nothing was sent");
    }

    /** A real server request, as WebFlux makes it: a body-less POST gives an empty Mono, which the mock does not. */
    private static ServerRequest real(String json) {
        MockServerHttpRequest.BodyBuilder b = MockServerHttpRequest.post("/internal/otp/send").contentType(MediaType.APPLICATION_JSON);
        MockServerWebExchange exchange = MockServerWebExchange.from(json == null ? b.build() : b.body(json));
        return ServerRequest.create(exchange, HandlerStrategies.withDefaults().messageReaders());
    }

    @Test
    void noBodyIsRefused() {
        rejected(real(null));
    }

    @Test
    void anEmptyJsonObjectOverTheRealReadersIsRefused() {
        rejected(real("{}"));
    }

    @Test
    void anEmptyObjectIsRefused() {
        rejected(MockServerRequest.builder().body(Mono.just(new OtpSendReqDto(null, null, null))));
    }

    @Test
    void eachMissingOrBlankPartIsRefused() {
        rejected(MockServerRequest.builder().body(Mono.just(new OtpSendReqDto(null, "a@example.com", "123456"))));
        rejected(MockServerRequest.builder().body(Mono.just(new OtpSendReqDto(NtfChnl.EMAIL, " ", "123456"))));
        rejected(MockServerRequest.builder().body(Mono.just(new OtpSendReqDto(NtfChnl.EMAIL, "a@example.com", ""))));
    }

    @Test
    void aCompleteRequestIsSent() {
        StepVerifier.create(handler.send(MockServerRequest.builder()
                        .body(Mono.just(new OtpSendReqDto(NtfChnl.EMAIL, "a@example.com", "123456")))))
                .expectNextCount(1)
                .verifyComplete();
        assertEquals(1, sent.get());
    }
}
