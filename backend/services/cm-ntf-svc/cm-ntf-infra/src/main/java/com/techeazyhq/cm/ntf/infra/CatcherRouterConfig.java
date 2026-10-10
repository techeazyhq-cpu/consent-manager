package com.techeazyhq.cm.ntf.infra;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import reactor.core.publisher.Mono;

/**
 * Deliberately lives in -infra, not -ifc.rest: this is dev/test tooling reading the catcher
 * adapters' own in-memory state (plan §9a), not part of the service's real domain-facing API.
 * Functional endpoints (plan §10's preference over {@code @RestController}), never wired into
 * a production profile.
 */
@Configuration
public class CatcherRouterConfig {

    @Bean
    public RouterFunction<ServerResponse> catcherRoutes(@Qualifier("smsCatcherStore") CatcherStore smsStore,
                                                          @Qualifier("whatsAppCatcherStore") CatcherStore whatsAppStore,
                                                          @Qualifier("pushCatcherStore") CatcherStore pushStore) {
        return RouterFunctions.route()
                .GET("/dev/sms-catcher", req -> list(smsStore))
                .GET("/dev/whatsapp-catcher", req -> list(whatsAppStore))
                .GET("/dev/push-catcher", req -> list(pushStore))
                .build();
    }

    private Mono<ServerResponse> list(CatcherStore store) {
        return ServerResponse.ok().bodyValue(store.list());
    }
}
