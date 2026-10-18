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

package com.techeazyhq.cm.gw;

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
import org.springframework.web.cors.reactive.CorsConfigurationSource;

import com.techeazyhq.cm.seckit.Audience;
import com.techeazyhq.cm.seckit.RealmTokens;

/**
 * The sandbox's gateway (ADR-038) lets one route through on a production DF Console sign-in: taking sandbox keys,
 * {@code POST /api/df-reg/sandbox-keys}. Production's fiduciary realm is believed there and nowhere else; every other
 * route still takes only the sandbox's own realms. Production's gateway has no such route.
 */
@Configuration
@ConditionalOnProperty(name = "cm.sandbox.enabled", havingValue = "true")
public class SandboxKeysSecurityConfig {

    static final String PATH = "/api/df-reg/sandbox-keys";

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityWebFilterChain sandboxKeysSecurity(
            ServerHttpSecurity http,
            CorsConfigurationSource corsConfigurationSource,
            @Value("${cm.sandbox.production.issuer-base:${cm.security.issuer-base:http://localhost:8080/realms/}}")
            String issuerBase,
            @Value("${cm.sandbox.production.jwks-base:${cm.security.jwks-base:http://localhost:8080/realms/}}")
            String jwksBase,
            @Value("${cm.sandbox.production.realm-prefix:}") String realmPrefix) {
        return http
                .securityMatcher(ServerWebExchangeMatchers.pathMatchers(HttpMethod.POST, PATH))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .authorizeExchange(ex -> ex.anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.authenticationManagerResolver(
                        RealmTokens.resolver(issuerBase, jwksBase, realmPrefix, Set.of(Audience.FIDUCIARY))))
                .build();
    }
}
