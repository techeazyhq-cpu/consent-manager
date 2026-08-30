package com.techeazyhq.cm.cnsqry;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

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
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(ex -> ex
                        .pathMatchers("/actuator/health", "/actuator/info", "/internal/**").permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {
                }))
                .build();
    }
}
