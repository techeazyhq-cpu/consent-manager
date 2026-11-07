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
import java.time.Instant;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.techeazyhq.cm.dsr.app.DpReqHandler;
import com.techeazyhq.cm.dsr.app.DsrCaller;
import com.techeazyhq.cm.dsr.dom.DpReq;
import com.techeazyhq.cm.seckit.AuthCtx;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Mono;

/**
 * Principal requests over HTTP (ADR-058), each side under its own path:
 * <ul>
 * <li>{@code /api/dsr/me/requests}: the principal's own requests and grievances;</li>
 * <li>{@code /api/dsr/df/requests}: requests relayed to the caller's fiduciary;</li>
 * <li>{@code /api/dsr/grievances}: grievances about the platform, for its staff.</li>
 * </ul>
 * The fiduciary's view carries the principal's words but never who they are beyond the consent the request concerns.
 */
public class DsrHttpHandler {

    private final DpReqHandler requests;
    private final Clock clock;

    public DsrHttpHandler(DpReqHandler requests, Clock clock) {
        this.requests = requests;
        this.clock = clock;
    }

    // ---- the principal ----

    public Mono<ServerResponse> mine(ServerRequest request) {
        return caller(request).flatMap(c -> requests.mine(c).map(this::dto).collectList())
                .flatMap(list -> ServerResponse.ok().bodyValue(list));
    }

    /** {@code kind} RELAY (the default) needs {@code cnsReqId} and {@code right}; GRIEVANCE needs {@code details}. */
    public Mono<ServerResponse> raise(ServerRequest request) {
        return caller(request)
                .zipWith(request.bodyToMono(RaiseDto.class).defaultIfEmpty(new RaiseDto(null, null, null, null)))
                .flatMap(t -> {
                    RaiseDto body = t.getT2();
                    boolean grievance = body.kind() != null && "GRIEVANCE".equalsIgnoreCase(body.kind().trim());
                    if (!grievance && body.kind() != null && !"RELAY".equalsIgnoreCase(body.kind().trim())) {
                        return Mono.error(new DomRuleViolation(400, "invalid-kind", "kind is RELAY or GRIEVANCE"));
                    }
                    return grievance ? requests.grievance(t.getT1(), body.details())
                            : requests.relay(t.getT1(), body.cnsReqId(), body.right(), body.details());
                })
                .flatMap(req -> ServerResponse.status(HttpStatus.CREATED).bodyValue(dto(req)));
    }

    public Mono<ServerResponse> withdraw(ServerRequest request) {
        String id = request.pathVariable("id");
        return caller(request).flatMap(c -> requests.withdraw(c, id))
                .flatMap(req -> ServerResponse.ok().bodyValue(dto(req)));
    }

    // ---- the fiduciary ----

    public Mono<ServerResponse> forFiduciary(ServerRequest request) {
        DpReq.Sts sts = sts(request);
        return caller(request).flatMap(c -> requests.forFiduciary(c, sts).map(this::dto).collectList())
                .flatMap(list -> ServerResponse.ok().bodyValue(list));
    }

    public Mono<ServerResponse> oneForFiduciary(ServerRequest request) {
        String id = request.pathVariable("id");
        return caller(request).flatMap(c -> requests.oneForFiduciary(c, id))
                .flatMap(req -> ServerResponse.ok().bodyValue(dto(req)));
    }

    // ---- either answerer: the fiduciary for its requests, staff for grievances ----

    public Mono<ServerResponse> grievances(ServerRequest request) {
        DpReq.Sts sts = sts(request);
        return caller(request).flatMap(c -> requests.grievances(c, sts).map(this::dto).collectList())
                .flatMap(list -> ServerResponse.ok().bodyValue(list));
    }

    public Mono<ServerResponse> acknowledge(ServerRequest request) {
        String id = request.pathVariable("id");
        return caller(request).flatMap(c -> requests.acknowledge(c, kind(request), id))
                .flatMap(req -> ServerResponse.ok().bodyValue(dto(req)));
    }

    public Mono<ServerResponse> resolve(ServerRequest request) {
        String id = request.pathVariable("id");
        return caller(request)
                .zipWith(request.bodyToMono(AnswerDto.class).defaultIfEmpty(new AnswerDto(null, null)))
                .flatMap(t -> requests.resolve(t.getT1(), kind(request), id, t.getT2().response()))
                .flatMap(req -> ServerResponse.ok().bodyValue(dto(req)));
    }

    public Mono<ServerResponse> refuse(ServerRequest request) {
        String id = request.pathVariable("id");
        return caller(request)
                .zipWith(request.bodyToMono(AnswerDto.class).defaultIfEmpty(new AnswerDto(null, null)))
                .flatMap(t -> requests.refuse(t.getT1(), kind(request), id, t.getT2().response(), t.getT2().ground()))
                .flatMap(req -> ServerResponse.ok().bodyValue(dto(req)));
    }

    /** Grievances are answered under {@code /api/dsr/grievances}, relayed requests under {@code /api/dsr/df}. */
    private static DpReq.Knd kind(ServerRequest request) {
        return request.path().startsWith("/api/dsr/grievances") ? DpReq.Knd.GRIEVANCE : DpReq.Knd.RELAY;
    }

    private static DpReq.Sts sts(ServerRequest request) {
        return request.queryParam("sts").filter(s -> !s.isBlank()).map(s -> {
            try {
                return DpReq.Sts.valueOf(s.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new DomRuleViolation(400, "invalid-sts",
                        "sts is OPEN, ACKNOWLEDGED, RESOLVED, REFUSED or WITHDRAWN");
            }
        }).orElse(null);
    }

    private static Mono<DsrCaller> caller(ServerRequest request) {
        return request.principal()
                .cast(JwtAuthenticationToken.class)
                .map(token -> {
                    Jwt jwt = (Jwt) token.getPrincipal();
                    AuthCtx ctx = AuthCtx.from(jwt);
                    String email = jwt.getClaimAsString("email");
                    // answering is what raising requests is: Owners, Administrators, Consent managers and systems
                    return new DsrCaller(ctx.subject(), email != null ? email : ctx.subject(), ctx.dpId(), ctx.dfId(),
                            ctx.mayRaiseCnsReqs(), ctx.isPlatformStaff(), jwt.getTokenValue());
                });
    }

    private DpReqDto dto(DpReq r) {
        return new DpReqDto(r.id(), r.knd().name(), r.dfId(), r.cnsReqId(), r.prpsCode(),
                r.rgt() == null ? null : r.rgt().name(), r.details(), r.sts().name(), r.receivedAt(), r.dueBy(),
                r.overdue(clock.instant()), r.acknowledgedAt(), r.closedAt(), r.closedByName(), r.response(),
                r.refusalGround());
    }

    record RaiseDto(String kind, String cnsReqId, String right, String details) {
    }

    record AnswerDto(String response, String ground) {
    }

    record DpReqDto(String id, String kind, String dfId, String cnsReqId, String prpsCode, String right, String details,
                    String sts, Instant receivedAt, Instant dueBy, boolean overdue, Instant acknowledgedAt,
                    Instant closedAt, String answeredBy, String response, String refusalGround) {
    }
}
