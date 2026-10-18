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

package com.techeazyhq.cm.cnsqry;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

import com.techeazyhq.cm.seckit.RealmTokens;

/**
 * Without this, Spring Boot's default resource-server auto-configuration requires a valid
 * JWT on every path — including {@code /internal/cns-arts/{id}/status}, which {@code cm-pdp}
 * (OPA, via Rego's {@code http.send}) calls with no Authorization header at all (plan §9).
 * {@code /api/**} still independently re-validates the JWT cm-gw already checked
 * (defense-in-depth, plan §9).
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http,
            @Value("${cm.security.issuer-base:http://localhost:8080/realms/}") String issuerBase,
            @Value("${cm.security.jwks-base:http://localhost:8080/realms/}") String jwksBase) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(ex -> ex
                        .pathMatchers("/actuator/health", "/actuator/info", "/internal/**").permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.authenticationManagerResolver(
                        RealmTokens.resolver(issuerBase, jwksBase)))
                .build();
    }
}
