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

import com.techeazyhq.cm.dsr.app.DpErasureHandler;
import com.techeazyhq.cm.dsr.app.DpReqHandler;
import com.techeazyhq.cm.dsr.dom.DpErasure;
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
    private final DpErasureHandler erasures;
    private final Clock clock;

    public DsrHttpHandler(DpReqHandler requests, DpErasureHandler erasures, Clock clock) {
        this.requests = requests;
        this.erasures = erasures;
        this.clock = clock;
    }

    // ---- closing an account (ADR-059) ----

    /** The principal closes their account; {@code confirmation} must be the words the portal asks them to type. */
    public Mono<ServerResponse> closeAccount(ServerRequest request) {
        return request.principal().cast(JwtAuthenticationToken.class)
                .map(token -> ((Jwt) token.getPrincipal()).getClaimAsString("email"))
                .defaultIfEmpty("")
                .zipWith(caller(request))
                .zipWith(request.bodyToMono(CloseDto.class).defaultIfEmpty(new CloseDto(null)))
                .flatMap(t -> erasures.request(t.getT1().getT2(), t.getT1().getT1().isBlank() ? null : t.getT1().getT1(),
                        t.getT2().confirmation()))
                .flatMap(e -> ServerResponse.status(HttpStatus.ACCEPTED).bodyValue(ErasureDto.of(e)));
    }

    public Mono<ServerResponse> myErasure(ServerRequest request) {
        return caller(request).flatMap(erasures::mine)
                .flatMap(e -> ServerResponse.ok().bodyValue(ErasureDto.of(e)))
                .switchIfEmpty(ServerResponse.noContent().build());
    }

    public Mono<ServerResponse> erasures(ServerRequest request) {
        DpErasure.Sts sts = request.queryParam("sts").filter(s -> !s.isBlank()).map(s -> {
            try {
                return DpErasure.Sts.valueOf(s.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new DomRuleViolation(400, "invalid-sts", "sts is RUNNING, DONE or STUCK");
            }
        }).orElse(null);
        return caller(request).flatMap(c -> erasures.list(c, sts).map(ErasureDto::of).collectList())
                .flatMap(list -> ServerResponse.ok().bodyValue(list));
    }

    public Mono<ServerResponse> retryErasure(ServerRequest request) {
        String id = request.pathVariable("id");
        return caller(request).flatMap(c -> erasures.retry(c, id))
                .flatMap(e -> ServerResponse.ok().bodyValue(ErasureDto.of(e)));
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

    record CloseDto(String confirmation) {
    }

    /** No email: staff see a pseudonymous id, the step and why it is stuck. */
    record ErasureDto(String id, String dpId, String sts, String step, int attempts, Instant requestedAt,
                      Instant finishedAt, String lastError, int consentsWithdrawn) {

        static ErasureDto of(DpErasure e) {
            return new ErasureDto(e.id(), e.dpId(), e.sts().name(), e.step() == null ? null : e.step().name(),
                    e.attempts(), e.requestedAt(), e.finishedAt(), e.lastError(), e.consentsWithdrawn());
        }
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
