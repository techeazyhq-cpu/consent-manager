package com.techeazyhq.cm.gw;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import reactor.core.publisher.Mono;

/**
 * Spring Security's default JWT authorities converter reads the standard {@code scope}/
 * {@code scp} claim, not our custom {@code roles} claim (plan §2, from Keycloak's
 * {@code oidc-usermodel-realm-role-mapper}) — without this, every {@code hasRole(...)} check
 * in {@link SecurityConfig} silently never matches. Maps {@code roles: ["CM_ADMIN"]} to the
 * {@code ROLE_CM_ADMIN} authority Spring Security's {@code hasRole} expects.
 */
@Configuration
public class JwtAuthConfig {

    @Bean
    public Converter<Jwt, Mono<AbstractAuthenticationToken>> jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName("roles");
        authoritiesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return new ReactiveJwtAuthenticationConverterAdapter(converter);
    }
}
