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

import java.time.Instant;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.techeazyhq.cm.dfreg.app.DfSignupHandler;
import com.techeazyhq.cm.dfreg.dom.Df;
import com.techeazyhq.cm.dfreg.dom.DfSts;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Mono;

/**
 * Self-service sign-up (ADR-065). {@code POST /api/df-reg/signup} and {@code POST /api/df-reg/signup/verify} are public:
 * the gateway lets them through unauthenticated and limits each address. {@code POST /api/df-reg/dfs/{id}/reject} is an
 * administrator declining an application.
 */
public class DfSignupHttpHandler {

    private final DfSignupHandler signups;

    public DfSignupHttpHandler(DfSignupHandler signups) {
        this.signups = signups;
    }

    /** Always the same answer for a valid application: whether or not the address is already known is not told. */
    public Mono<ServerResponse> apply(ServerRequest request) {
        return request.bodyToMono(ApplyReqDto.class)
                .switchIfEmpty(Mono.error(new DomRuleViolation(400, "invalid-request", "An application is needed")))
                .flatMap(b -> signups.apply(new DfSignupHandler.Application(b.name(), b.contactEmail(), b.legalName(), b.industry(), b.website())))
                .then(Mono.defer(() -> ServerResponse.status(HttpStatus.ACCEPTED).bodyValue(Map.of(
                        "message", "If the address can receive mail, a link to confirm it is on its way."))));
    }

    public Mono<ServerResponse> verify(ServerRequest request) {
        return request.bodyToMono(VerifyReqDto.class).defaultIfEmpty(new VerifyReqDto(null, null))
                .flatMap(b -> signups.verify(b.id(), b.token()))
                .then(Mono.defer(() -> ServerResponse.ok().bodyValue(Map.of("status", "RECEIVED"))));
    }

    public Mono<ServerResponse> reject(ServerRequest request) {
        String id = request.pathVariable("id");
        return request.bodyToMono(RejectReqDto.class).defaultIfEmpty(new RejectReqDto(null))
                .flatMap(b -> signups.reject(id, b.reason()))
                .flatMap(df -> ServerResponse.ok().bodyValue(RejectedDto.of(df)));
    }

    record ApplyReqDto(String name, String contactEmail, String legalName, String industry, String website) {
    }

    record VerifyReqDto(String id, String token) {
    }

    record RejectReqDto(String reason) {
    }

    record RejectedDto(String id, DfSts sts, Instant rejectedAt, String rejectionReason) {

        static RejectedDto of(Df df) {
            return new RejectedDto(df.id(), df.sts(), df.rejectedAt(), df.rejectionReason());
        }
    }
}
