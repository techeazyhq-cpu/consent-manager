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

package com.techeazyhq.cm.bil.ifc.rest;

import java.time.Instant;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.techeazyhq.cm.bil.app.BilCaller;
import com.techeazyhq.cm.bil.app.BilOverviewQryHandler;
import com.techeazyhq.cm.bil.app.GetDfSubStatusQryHandler;
import com.techeazyhq.cm.bil.app.PlnChgReqHandler;
import com.techeazyhq.cm.bil.dom.PlnChgReq;
import com.techeazyhq.cm.seckit.AuthCtx;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Mono;

/**
 * Billing as fiduciaries and platform staff see it (ADR-039). {@code /api/bil/me/**} is the caller's own fiduciary,
 * from the {@code df_id} in their token: its subscription and its plan change requests. The overview and the request
 * queue are for platform staff.
 */
public class BilAccountHttpHandler {

    private final GetDfSubStatusQryHandler status;
    private final BilOverviewQryHandler overview;
    private final PlnChgReqHandler requests;

    public BilAccountHttpHandler(GetDfSubStatusQryHandler status, BilOverviewQryHandler overview,
                                 PlnChgReqHandler requests) {
        this.status = status;
        this.overview = overview;
        this.requests = requests;
    }

    public Mono<ServerResponse> mySubscription(ServerRequest request) {
        return caller(request).flatMap(c -> c.isDf()
                ? status.handle(c.dfId()).flatMap(s -> ServerResponse.ok().bodyValue(s))
                : Mono.error(new DomRuleViolation(403, "not-a-fiduciary", "Only a fiduciary has a subscription")));
    }

    public Mono<ServerResponse> myRequests(ServerRequest request) {
        return caller(request).flatMap(c -> requests.mine(c).map(ReqDto::of).collectList())
                .flatMap(list -> ServerResponse.ok().bodyValue(list));
    }

    public Mono<ServerResponse> requestChange(ServerRequest request) {
        return caller(request)
                .zipWith(request.bodyToMono(ChangeReqDto.class).defaultIfEmpty(new ChangeReqDto(null, null)))
                .flatMap(t -> requests.request(t.getT1(), t.getT2().subPlnName(), t.getT2().note()))
                .flatMap(req -> ServerResponse.status(HttpStatus.CREATED).bodyValue(ReqDto.of(req)));
    }

    public Mono<ServerResponse> withdraw(ServerRequest request) {
        String id = request.pathVariable("id");
        return caller(request).flatMap(c -> requests.withdraw(c, id))
                .flatMap(req -> ServerResponse.ok().bodyValue(ReqDto.of(req)));
    }

    public Mono<ServerResponse> overview(ServerRequest request) {
        return caller(request)
                .flatMap(c -> c.staff() ? overview.handle()
                        : Mono.error(new DomRuleViolation(403, "not-platform-staff", "Only platform staff see every tenant")))
                .flatMap(rows -> ServerResponse.ok().bodyValue(rows.stream()
                        .map(r -> new OverviewRowDto(r.dfId(), r.sub().subPlnName(), r.sub().dfSubSts().name(),
                                r.sub().curPeriodUsg(), r.sub().reqPerMonthQta(), r.sub().reqPerSecQta()))
                        .toList()));
    }

    public Mono<ServerResponse> list(ServerRequest request) {
        PlnChgReq.Sts sts = request.queryParam("sts").filter(s -> !s.isBlank()).map(s -> {
            try {
                return PlnChgReq.Sts.valueOf(s.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new DomRuleViolation(400, "invalid-sts", "sts is PENDING, APPROVED, DECLINED or WITHDRAWN");
            }
        }).orElse(null);
        return caller(request).flatMap(c -> requests.list(c, sts).map(ReqDto::of).collectList())
                .flatMap(list -> ServerResponse.ok().bodyValue(list));
    }

    public Mono<ServerResponse> approve(ServerRequest request) {
        return decide(request, true);
    }

    public Mono<ServerResponse> decline(ServerRequest request) {
        return decide(request, false);
    }

    private Mono<ServerResponse> decide(ServerRequest request, boolean approve) {
        String id = request.pathVariable("id");
        return caller(request)
                .zipWith(request.bodyToMono(DecisionDto.class).defaultIfEmpty(new DecisionDto(null)))
                .flatMap(t -> approve ? requests.approve(t.getT1(), id, t.getT2().note())
                        : requests.decline(t.getT1(), id, t.getT2().note()))
                .flatMap(req -> ServerResponse.ok().bodyValue(ReqDto.of(req)));
    }

    private static Mono<BilCaller> caller(ServerRequest request) {
        return request.principal()
                .cast(JwtAuthenticationToken.class)
                .map(token -> {
                    Jwt jwt = (Jwt) token.getPrincipal();
                    AuthCtx ctx = AuthCtx.from(jwt);
                    String email = jwt.getClaimAsString("email");
                    boolean person = ctx.roles().stream().anyMatch(r -> r.startsWith("DF_"));
                    boolean mayRequest = person && (ctx.roles().contains("DF_OWNER") || ctx.roles().contains("DF_ADMIN"));
                    return new BilCaller(ctx.subject(), email != null ? email : ctx.subject(), ctx.dfId(), mayRequest,
                            ctx.isPlatformStaff(), ctx.isCmAdmin());
                });
    }

    record ChangeReqDto(String subPlnName, String note) {
    }

    record DecisionDto(String note) {
    }

    record OverviewRowDto(String dfId, String subPlnName, String dfSubSts, long curPeriodUsg, long reqPerMonthQta,
                          long reqPerSecQta) {
    }

    record ReqDto(String id, String dfId, String fromPlnName, String toPlnName, String note, String sts,
                  String requestedByName, Instant requestedAt, String decidedByName, Instant decidedAt,
                  String decisionNote) {

        static ReqDto of(PlnChgReq r) {
            return new ReqDto(r.id(), r.dfId(), r.fromPlnName(), r.toPlnName(), r.note(), r.sts().name(),
                    r.requestedByName(), r.requestedAt(), r.decidedByName(), r.decidedAt(), r.decisionNote());
        }
    }
}
