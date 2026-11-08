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

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.techeazyhq.cm.dfreg.app.DfEndContractHandler;
import com.techeazyhq.cm.dfreg.dom.LeaveProposal;
import com.techeazyhq.cm.seckit.AuthCtx;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Mono;

/**
 * Platform staff ending a fiduciary's contract with a second person's approval (ADR-064 D7).
 * {@code POST /api/df-reg/dfs/{id}/end-contract} and {@code .../cancel-end-contract} propose (a reason is needed);
 * {@code GET /api/df-reg/leave-proposals?status=PENDING&dfId=} lists; {@code POST /api/df-reg/leave-proposals/{id}/approve},
 * {@code /reject} and {@code /withdraw} decide. The person who proposed cannot approve or reject.
 */
public class DfEndContractHttpHandler {

    private final DfEndContractHandler handler;

    public DfEndContractHttpHandler(DfEndContractHandler handler) {
        this.handler = handler;
    }

    public Mono<ServerResponse> proposeEnd(ServerRequest request) {
        return propose(request, LeaveProposal.Kind.START);
    }

    public Mono<ServerResponse> proposeCancel(ServerRequest request) {
        return propose(request, LeaveProposal.Kind.CANCEL);
    }

    public Mono<ServerResponse> list(ServerRequest request) {
        LeaveProposal.Status status;
        try {
            status = LeaveProposal.Status.valueOf(request.queryParam("status").orElse("PENDING").toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Mono.error(new DomRuleViolation(400, "invalid-request", "status is PENDING, APPROVED, REJECTED or WITHDRAWN"));
        }
        return staff(request).flatMap(id -> handler.list(status, request.queryParam("dfId").orElse(null)).map(ProposalDto::of).collectList())
                .flatMap(list -> ServerResponse.ok().bodyValue(list));
    }

    public Mono<ServerResponse> approve(ServerRequest request) {
        return staff(request).flatMap(id -> handler.approve(id, request.pathVariable("id"))).flatMap(p -> ServerResponse.ok().bodyValue(ProposalDto.of(p)));
    }

    public Mono<ServerResponse> reject(ServerRequest request) {
        return staff(request).zipWith(request.bodyToMono(NoteDto.class).defaultIfEmpty(new NoteDto(null)))
                .flatMap(t -> handler.reject(t.getT1(), request.pathVariable("id"), t.getT2().note()))
                .flatMap(p -> ServerResponse.ok().bodyValue(ProposalDto.of(p)));
    }

    public Mono<ServerResponse> withdraw(ServerRequest request) {
        return staff(request).flatMap(id -> handler.withdraw(id, request.pathVariable("id"))).flatMap(p -> ServerResponse.ok().bodyValue(ProposalDto.of(p)));
    }

    private Mono<ServerResponse> propose(ServerRequest request, LeaveProposal.Kind kind) {
        String dfId = request.pathVariable("id");
        return staff(request).zipWith(request.bodyToMono(ReasonDto.class).defaultIfEmpty(new ReasonDto(null)))
                .flatMap(t -> handler.propose(t.getT1(), dfId, kind, t.getT2().reason()))
                .flatMap(p -> ServerResponse.status(201).bodyValue(ProposalDto.of(p)));
    }

    private static Mono<String> staff(ServerRequest request) {
        return request.principal().cast(JwtAuthenticationToken.class)
                .map(token -> AuthCtx.from((Jwt) token.getPrincipal()))
                .flatMap(ctx -> ctx.isPlatformStaff() ? Mono.just(ctx.subject())
                        : Mono.<String>error(new DomRuleViolation(403, "not-permitted", "Only platform staff")));
    }

    record ReasonDto(String reason) {
    }

    record NoteDto(String note) {
    }

    record ProposalDto(String id, String dfId, String kind, String reason, String proposedBy, Instant proposedAt, String status,
                       String decidedBy, Instant decidedAt, String decisionNote) {

        static ProposalDto of(LeaveProposal p) {
            return new ProposalDto(p.id(), p.dfId(), p.kind().name(), p.reason(), p.proposedBy(), p.proposedAt(), p.status().name(),
                    p.decidedBy(), p.decidedAt(), p.decisionNote());
        }
    }
}
