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

package com.techeazyhq.cm.bil;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

import com.techeazyhq.cm.seckit.RealmTokens;

/**
 * Every API call needs a valid token, re-validated here although cm-gw already checked it (defense in depth), with
 * the gateway's role rules repeated. A fiduciary's own billing ({@code /api/bil/me/**}) is decided from its token by
 * {@code BilAccountHttpHandler}. {@code /internal/**} is for other services on the private network only (cm-gw
 * doesn't route it): quota checks and batch reservations.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    private static final String[] STAFF = {"CM_ADMIN", "CM_OPERATOR"};

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http,
            @Value("${cm.security.issuer-base:http://localhost:8080/realms/}") String issuerBase,
            @Value("${cm.security.jwks-base:http://localhost:8080/realms/}") String jwksBase,
            @Value("${cm.security.realm-prefix:}") String realmPrefix) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(ex -> ex
                        .pathMatchers("/actuator/health", "/actuator/info", "/internal/**").permitAll()
                        .pathMatchers("/api/bil/me/**").authenticated()
                        .pathMatchers(HttpMethod.GET, "/api/bil/sub-plns").authenticated()
                        .pathMatchers(HttpMethod.GET, "/api/bil/**").hasAnyRole(STAFF)
                        .pathMatchers("/api/bil/**").hasRole("CM_ADMIN")
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.authenticationManagerResolver(
                        RealmTokens.resolver(issuerBase, jwksBase, realmPrefix)))
                .build();
    }
}
