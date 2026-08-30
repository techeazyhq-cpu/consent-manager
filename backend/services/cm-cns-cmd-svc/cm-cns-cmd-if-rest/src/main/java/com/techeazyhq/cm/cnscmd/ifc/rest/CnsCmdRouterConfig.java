package com.techeazyhq.cm.cnscmd.ifc.rest;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.techeazyhq.cm.cnscmd.app.ApproveCnsCmdHandler;
import com.techeazyhq.cm.cnscmd.app.CreateCnsReqCmdHandler;
import com.techeazyhq.cm.cnscmd.app.DenyCnsReqCmdHandler;
import com.techeazyhq.cm.cnscmd.app.LinkDpCmdHandler;
import com.techeazyhq.cm.cnscmd.app.RevokeCnsArtCmdHandler;

/**
 * The service's only route table (plan §10, §13) — functional endpoints (WebFlux
 * {@code RouterFunction}/{@code HandlerFunction}) rather than {@code @RestController}
 * annotations, per project preference. Handler instances are plain objects, not Spring
 * beans, composed here from the {@code -app} command handlers that are beans.
 */
@Configuration
public class CnsCmdRouterConfig {

    @Bean
    public RouterFunction<ServerResponse> cnsCmdRoutes(CreateCnsReqCmdHandler createHandler,
                                                         ApproveCnsCmdHandler approveHandler,
                                                         DenyCnsReqCmdHandler denyHandler,
                                                         RevokeCnsArtCmdHandler revokeHandler,
                                                         LinkDpCmdHandler linkDpHandler) {
        CnsReqHandler cnsReqHandler = new CnsReqHandler(createHandler, approveHandler, denyHandler);
        CnsArtHandler cnsArtHandler = new CnsArtHandler(revokeHandler);
        DpLinkHandler dpLinkHandler = new DpLinkHandler(linkDpHandler);

        return RouterFunctions.route()
                .POST("/api/cns-cmd/cns-reqs", cnsReqHandler::create)
                .POST("/api/cns-cmd/cns-reqs/{id}/approve", cnsReqHandler::approve)
                .POST("/api/cns-cmd/cns-reqs/{id}/deny", cnsReqHandler::deny)
                .POST("/api/cns-cmd/cns-arts/{id}/revoke", cnsArtHandler::revoke)
                .POST("/api/cns-cmd/dp-links", dpLinkHandler::link)
                .build();
    }
}
