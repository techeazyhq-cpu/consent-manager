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

import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.techeazyhq.cm.bil.app.AssignSubPlnCmd;
import com.techeazyhq.cm.bil.app.AssignSubPlnCmdHandler;
import com.techeazyhq.cm.bil.app.GetDfSubStatusQryHandler;
import com.techeazyhq.cm.bil.app.GetUsgQryHandler;
import com.techeazyhq.cm.bil.app.ListSubPlnsQryHandler;
import com.techeazyhq.cm.bil.app.SetDfSubStsCmd;
import com.techeazyhq.cm.bil.app.SetDfSubStsCmdHandler;
import com.techeazyhq.cm.bil.app.SubEligQryHandler;
import com.techeazyhq.cm.bil.app.UsgReservationHandler;
import com.techeazyhq.cm.bil.dom.SubPln;

import reactor.core.publisher.Mono;

/** Functional-endpoint handler (plan §10's preference over {@code @RestController}). */
public class BilHandler {

    private final AssignSubPlnCmdHandler assignSubPlnHandler;
    private final SetDfSubStsCmdHandler setDfSubStsHandler;
    private final GetUsgQryHandler getUsgHandler;
    private final SubEligQryHandler subEligHandler;
    private final ListSubPlnsQryHandler listSubPlnsHandler;
    private final GetDfSubStatusQryHandler getDfSubStatusHandler;
    private final UsgReservationHandler usgReservationHandler;

    public BilHandler(AssignSubPlnCmdHandler assignSubPlnHandler, SetDfSubStsCmdHandler setDfSubStsHandler,
                       GetUsgQryHandler getUsgHandler, SubEligQryHandler subEligHandler,
                       ListSubPlnsQryHandler listSubPlnsHandler, GetDfSubStatusQryHandler getDfSubStatusHandler,
                      UsgReservationHandler usgReservationHandler) {
        this.assignSubPlnHandler = assignSubPlnHandler;
        this.setDfSubStsHandler = setDfSubStsHandler;
        this.getUsgHandler = getUsgHandler;
        this.subEligHandler = subEligHandler;
        this.listSubPlnsHandler = listSubPlnsHandler;
        this.getDfSubStatusHandler = getDfSubStatusHandler;
        this.usgReservationHandler = usgReservationHandler;
    }

    public Mono<ServerResponse> listSubPlns(ServerRequest request) {
        return ServerResponse.ok().body(listSubPlnsHandler.handle(), SubPln.class);
    }

    public Mono<ServerResponse> subPln(ServerRequest request) {
        return listSubPlnsHandler.byName(request.pathVariable("name"))
                .flatMap(plan -> ServerResponse.ok().bodyValue(plan))
                .switchIfEmpty(ServerResponse.notFound().build());
    }

    public Mono<ServerResponse> subStatus(ServerRequest request) {
        return getDfSubStatusHandler.handle(request.pathVariable("id"))
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    public Mono<ServerResponse> assignSubPln(ServerRequest request) {
        String dfId = request.pathVariable("id");
        return request.bodyToMono(AssignSubPlnReqDto.class)
                .flatMap(body -> assignSubPlnHandler.handle(new AssignSubPlnCmd(dfId, body.subPlnName())))
                .flatMap(dfSub -> ServerResponse.ok().bodyValue(dfSub));
    }

    public Mono<ServerResponse> setSubSts(ServerRequest request) {
        String dfId = request.pathVariable("id");
        return request.bodyToMono(SetSubStsReqDto.class)
                .flatMap(body -> setDfSubStsHandler.handle(new SetDfSubStsCmd(dfId, body.active())))
                .flatMap(dfSub -> ServerResponse.ok().bodyValue(dfSub));
    }

    public Mono<ServerResponse> usage(ServerRequest request) {
        return getUsgHandler.handle(request.pathVariable("id"))
                .flatMap(usage -> ServerResponse.ok().bodyValue(new UsageRespDto(usage)));
    }

    public Mono<ServerResponse> eligibility(ServerRequest request) {
        return subEligHandler.handle(request.pathVariable("id"))
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    /** Reserves a batch's allowance, all or nothing (ADR-036): {@code reserved} false, with what is left, when it doesn't fit. */
    public Mono<ServerResponse> reserveUsage(ServerRequest request) {
        String dfId = request.pathVariable("id");
        return request.bodyToMono(CountReqDto.class)
                .defaultIfEmpty(new CountReqDto(0))
                .flatMap(body -> usgReservationHandler.reserve(dfId, body.count()))
                .flatMap(reservation -> ServerResponse.ok().bodyValue(reservation));
    }

    /** Gives back what a batch reserved but didn't send. */
    public Mono<ServerResponse> releaseUsage(ServerRequest request) {
        String dfId = request.pathVariable("id");
        return request.bodyToMono(CountReqDto.class)
                .defaultIfEmpty(new CountReqDto(0))
                .flatMap(body -> usgReservationHandler.release(dfId, body.count()))
                .then(ServerResponse.noContent().build());
    }

    record CountReqDto(long count) {
    }

    record AssignSubPlnReqDto(String subPlnName) {
    }

    record SetSubStsReqDto(boolean active) {
    }

    record UsageRespDto(long currentPeriodUsage) {
    }
}
