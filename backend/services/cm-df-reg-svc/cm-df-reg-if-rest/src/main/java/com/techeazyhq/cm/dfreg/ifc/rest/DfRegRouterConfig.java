package com.techeazyhq.cm.dfreg.ifc.rest;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.techeazyhq.cm.dfreg.app.ActivateDfCmdHandler;
import com.techeazyhq.cm.dfreg.app.GetDfActiveQryHandler;
import com.techeazyhq.cm.dfreg.app.GetDfQryHandler;
import com.techeazyhq.cm.dfreg.app.ListDfsQryHandler;
import com.techeazyhq.cm.dfreg.app.RegisterDfCmdHandler;

@Configuration
public class DfRegRouterConfig {

    @Bean
    public RouterFunction<ServerResponse> dfRegRoutes(RegisterDfCmdHandler registerHandler,
                                                        ActivateDfCmdHandler activateHandler,
                                                        GetDfActiveQryHandler getActiveHandler,
                                                        ListDfsQryHandler listHandler,
                                                        GetDfQryHandler getDfHandler) {
        DfHandler dfHandler = new DfHandler(registerHandler, activateHandler, getActiveHandler, listHandler, getDfHandler);
        return RouterFunctions.route()
                .GET("/api/df-reg/dfs", dfHandler::list)
                .POST("/api/df-reg/dfs", dfHandler::register)
                .POST("/api/df-reg/dfs/{id}/activate", dfHandler::activate)
                .GET("/api/df-reg/dfs/{id}", dfHandler::getOne)
                .GET("/internal/dfs/{id}/active", dfHandler::isActive)
                .build();
    }
}
