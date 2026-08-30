package com.techeazyhq.cm.bil.app;

import com.techeazyhq.cm.bil.dom.UsgPort;

import reactor.core.publisher.Mono;

public class GetUsgQryHandler {

    private final UsgPort usgPort;

    public GetUsgQryHandler(UsgPort usgPort) {
        this.usgPort = usgPort;
    }

    public Mono<Long> handle(String dfId) {
        return usgPort.currentPeriodUsage(dfId);
    }
}
