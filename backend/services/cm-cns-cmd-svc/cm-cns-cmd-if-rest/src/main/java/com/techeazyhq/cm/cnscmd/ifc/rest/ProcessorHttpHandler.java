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

package com.techeazyhq.cm.cnscmd.ifc.rest;

import java.time.Instant;
import java.util.function.Function;

import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.techeazyhq.cm.cnscmd.app.ProcessorCmds;
import com.techeazyhq.cm.cnscmd.dom.Processor;
import com.techeazyhq.cm.seckit.AuthCtx;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Mono;

/**
 * A fiduciary's register of processors (ADR-075).
 * <ul>
 *   <li>{@code GET /api/cns-cmd/processors}: the register.</li>
 *   <li>{@code POST /api/cns-cmd/processors}: add one: {@code name}, and optionally {@code role}, {@code country},
 *       {@code contactEmail}.</li>
 *   <li>{@code PUT .../processors/{id}}: change its role, country or contact.</li>
 *   <li>{@code GET .../processors/{id}/uses}: the purposes that name it.</li>
 *   <li>{@code POST .../processors/{id}/retire} and {@code .../reinstate}.</li>
 * </ul>
 * Reading is open to all the fiduciary's people; changes to the roles that manage purposes (ADR-033), since the register
 * decides what a purpose may name.
 */
public class ProcessorHttpHandler {

    private final ProcessorCmds cmds;

    public ProcessorHttpHandler(ProcessorCmds cmds) {
        this.cmds = cmds;
    }

    public Mono<ServerResponse> list(ServerRequest request) {
        return asDf(request, false, ctx -> cmds.list(ctx.dfId()).map(Dto::of).collectList()
                .flatMap(list -> ServerResponse.ok().bodyValue(list)));
    }

    public Mono<ServerResponse> register(ServerRequest request) {
        return asDf(request, true, ctx -> body(request)
                .flatMap(b -> cmds.register(ctx.dfId(), b.name(), b.role(), b.country(), b.contactEmail()))
                .flatMap(p -> ServerResponse.status(HttpStatus.CREATED).bodyValue(Dto.of(p))));
    }

    public Mono<ServerResponse> describe(ServerRequest request) {
        return asDf(request, true, ctx -> body(request)
                .flatMap(b -> cmds.describe(ctx.dfId(), request.pathVariable("id"), b.role(), b.country(), b.contactEmail()))
                .flatMap(p -> ServerResponse.ok().bodyValue(Dto.of(p))));
    }

    public Mono<ServerResponse> uses(ServerRequest request) {
        return asDf(request, false, ctx -> cmds.uses(ctx.dfId(), request.pathVariable("id"))
                .flatMap(list -> ServerResponse.ok().bodyValue(list)));
    }

    public Mono<ServerResponse> retire(ServerRequest request) {
        return asDf(request, true, ctx -> cmds.retire(ctx.dfId(), request.pathVariable("id"))
                .flatMap(p -> ServerResponse.ok().bodyValue(Dto.of(p))));
    }

    public Mono<ServerResponse> reinstate(ServerRequest request) {
        return asDf(request, true, ctx -> cmds.reinstate(ctx.dfId(), request.pathVariable("id"))
                .flatMap(p -> ServerResponse.ok().bodyValue(Dto.of(p))));
    }

    private static Mono<BodyDto> body(ServerRequest request) {
        return request.bodyToMono(BodyDto.class)
                .switchIfEmpty(Mono.error(() -> new DomRuleViolation(400, "invalid-processor", "The request body is empty")));
    }

    /** Fiduciary callers only; changing the register needs a role that manages purposes (ADR-033), reading any of its people. */
    private static Mono<ServerResponse> asDf(ServerRequest request, boolean changes,
                                             Function<AuthCtx, Mono<ServerResponse>> action) {
        return CnsReqHandler.authCtx(request)
                .filter(AuthCtx::isDf)
                .flatMap(ctx -> changes && !ctx.mayManagePurposes()
                        ? Mono.<ServerResponse>error(new DomRuleViolation(403, "not-permitted",
                                "Your role at this fiduciary doesn't manage purposes"))
                        : action.apply(ctx))
                .switchIfEmpty(Mono.error(() -> new DomRuleViolation(403, "not-permitted",
                        "Only a fiduciary keeps a register of processors")));
    }

    record BodyDto(String name, String role, String country, String contactEmail) { }

    record Dto(String id, String name, String role, String country, String contactEmail, String status, Instant createdAt,
               Instant retiredAt) {
        static Dto of(Processor p) {
            return new Dto(p.id(), p.name(), p.role(), p.country(), p.contactEmail(), p.sts().name(), p.createdAt(),
                    p.retiredAt());
        }
    }
}
