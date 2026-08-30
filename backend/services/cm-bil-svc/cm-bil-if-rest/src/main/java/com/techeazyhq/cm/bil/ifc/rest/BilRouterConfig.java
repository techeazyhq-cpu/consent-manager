package com.techeazyhq.cm.bil.ifc.rest;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.techeazyhq.cm.bil.app.AssignSubPlnCmdHandler;
import com.techeazyhq.cm.bil.app.GetDfSubStatusQryHandler;
import com.techeazyhq.cm.bil.app.GetUsgQryHandler;
import com.techeazyhq.cm.bil.app.ListSubPlnsQryHandler;
import com.techeazyhq.cm.bil.app.SetDfSubStsCmdHandler;
import com.techeazyhq.cm.bil.app.SubEligQryHandler;

@Configuration
public class BilRouterConfig {

    @Bean
    public RouterFunction<ServerResponse> bilRoutes(AssignSubPlnCmdHandler assignSubPlnHandler,
                                                      SetDfSubStsCmdHandler setDfSubStsHandler,
                                                      GetUsgQryHandler getUsgHandler,
                                                      SubEligQryHandler subEligHandler,
                                                      ListSubPlnsQryHandler listSubPlnsHandler,
                                                      GetDfSubStatusQryHandler getDfSubStatusHandler) {
        BilHandler handler = new BilHandler(assignSubPlnHandler, setDfSubStsHandler, getUsgHandler, subEligHandler,
                listSubPlnsHandler, getDfSubStatusHandler);
        return RouterFunctions.route()
                .GET("/api/bil/sub-plns", handler::listSubPlns)
                .PUT("/api/bil/dfs/{id}/sub-pln", handler::assignSubPln)
                .PUT("/api/bil/dfs/{id}/sub-status", handler::setSubSts)
                .GET("/api/bil/dfs/{id}/usage", handler::usage)
                .GET("/api/bil/dfs/{id}/sub-status", handler::subStatus)
                .GET("/internal/dfs/{id}/sub-eligibility", handler::eligibility)
                .build();
    }
}
