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

package com.techeazyhq.cm.dfreg;

import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.techeazyhq.cm.dfreg.app.ActivateDfCmdHandler;
import com.techeazyhq.cm.dfreg.app.IssueSandboxKeysHandler;
import com.techeazyhq.cm.dfreg.dom.DfApiClientProvisionPort;
import com.techeazyhq.cm.dfreg.dom.DfRepoPort;
import com.techeazyhq.cm.dfreg.ifc.rest.SandboxKeysHttpHandler;
import com.techeazyhq.cm.seckit.Audience;
import com.techeazyhq.cm.seckit.RealmTokens;

/**
 * Sandbox keys (ADR-038), present only in the sandbox ({@code cm.sandbox.enabled}). Its one route has a security chain
 * of its own that believes production's fiduciary realm, and no other realm: a production DF Console sign-in says who
 * is asking, nothing more. Every other route keeps trusting the sandbox's realms only.
 */
@Configuration
@ConditionalOnProperty(name = "cm.sandbox.enabled", havingValue = "true")
public class SandboxKeysConfig {

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityWebFilterChain sandboxKeysSecurity(
            ServerHttpSecurity http,
            @Value("${cm.sandbox.production.issuer-base:${cm.security.issuer-base:http://localhost:8080/realms/}}")
            String issuerBase,
            @Value("${cm.sandbox.production.jwks-base:${cm.security.jwks-base:http://localhost:8080/realms/}}")
            String jwksBase,
            @Value("${cm.sandbox.production.realm-prefix:}") String realmPrefix) {
        return http
                .securityMatcher(ServerWebExchangeMatchers.pathMatchers(HttpMethod.POST, SandboxKeysHttpHandler.PATH))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(ex -> ex.anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.authenticationManagerResolver(
                        RealmTokens.resolver(issuerBase, jwksBase, realmPrefix, Set.of(Audience.FIDUCIARY))))
                .build();
    }

    @Bean
    public IssueSandboxKeysHandler issueSandboxKeysHandler(DfRepoPort dfRepo, ActivateDfCmdHandler activate,
                                                           DfApiClientProvisionPort clients) {
        return new IssueSandboxKeysHandler(dfRepo, activate, clients);
    }

    /** The token endpoint handed out with the keys: the sandbox fiduciary realm's, as browsers and systems reach it. */
    @Bean
    public RouterFunction<ServerResponse> sandboxKeysRoutes(
            IssueSandboxKeysHandler keys,
            @Value("${cm.security.issuer-base:http://localhost:8080/realms/}") String issuerBase,
            @Value("${cm.keycloak.realm}") String realm) {
        String base = issuerBase.endsWith("/") ? issuerBase : issuerBase + "/";
        SandboxKeysHttpHandler handler =
                new SandboxKeysHttpHandler(keys, base + realm + "/protocol/openid-connect/token");
        return RouterFunctions.route().POST(SandboxKeysHttpHandler.PATH, handler::issue).build();
    }
}
