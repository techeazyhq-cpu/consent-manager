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

    public BilHandler(AssignSubPlnCmdHandler assignSubPlnHandler, SetDfSubStsCmdHandler setDfSubStsHandler,
                       GetUsgQryHandler getUsgHandler, SubEligQryHandler subEligHandler,
                       ListSubPlnsQryHandler listSubPlnsHandler, GetDfSubStatusQryHandler getDfSubStatusHandler) {
        this.assignSubPlnHandler = assignSubPlnHandler;
        this.setDfSubStsHandler = setDfSubStsHandler;
        this.getUsgHandler = getUsgHandler;
        this.subEligHandler = subEligHandler;
        this.listSubPlnsHandler = listSubPlnsHandler;
        this.getDfSubStatusHandler = getDfSubStatusHandler;
    }

    public Mono<ServerResponse> listSubPlns(ServerRequest request) {
        return ServerResponse.ok().body(listSubPlnsHandler.handle(), SubPln.class);
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

    record AssignSubPlnReqDto(String subPlnName) {
    }

    record SetSubStsReqDto(boolean active) {
    }

    record UsageRespDto(long currentPeriodUsage) {
    }
}
