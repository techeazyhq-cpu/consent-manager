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

package com.techeazyhq.cm.seckit;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.ReactiveAuthenticationManagerResolver;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtIssuerReactiveAuthenticationManagerResolver;
import org.springframework.security.oauth2.server.resource.authentication.JwtReactiveAuthenticationManager;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * Accepts tokens from the three realms (ADR-035) and from nowhere else. A token's issuer picks its realm's signing
 * keys and must be exactly that realm's address; its {@code roles} become {@code ROLE_} authorities only where the
 * realm may assert them ({@link Audience}), so {@code hasRole} checks see the same filtered roles as {@link AuthCtx}.
 *
 * <p>Two bases, because the issuer a browser sees ({@code http://localhost:8080/realms/}) and the address services
 * fetch keys from inside the network ({@code http://keycloak:8080/realms/}) can differ.
 *
 * <p>A sandbox (ADR-034, ADR-037) runs the same services against its own realms, named with a prefix
 * ({@code sbx-cm-df}): their own users, keys and issuers, so a sandbox token never verifies in production, and the
 * reverse.
 */
public final class RealmTokens {

    private RealmTokens() {
    }

    /**
     * @param issuerBase what tokens carry as {@code iss}, up to the realm name, e.g. {@code http://localhost:8080/realms/}
     * @param jwksBase   where this service reaches Keycloak, up to the realm name
     */
    public static ReactiveAuthenticationManagerResolver<ServerWebExchange> resolver(String issuerBase, String jwksBase) {
        return resolver(issuerBase, jwksBase, "");
    }

    /**
     * @param realmPrefix put before each realm's name: empty in production, {@code sbx-} in the sandbox
     */
    public static ReactiveAuthenticationManagerResolver<ServerWebExchange> resolver(String issuerBase, String jwksBase,
                                                                                    String realmPrefix) {
        Map<String, ReactiveAuthenticationManager> byIssuer = new HashMap<>();
        trustedIssuers(issuerBase, realmPrefix).forEach((issuer, audience) -> {
            String realm = issuer.substring(withSlash(issuerBase).length());
            NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder
                    .withJwkSetUri(withSlash(jwksBase) + realm + "/protocol/openid-connect/certs")
                    .build();
            decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
            JwtReactiveAuthenticationManager manager = new JwtReactiveAuthenticationManager(decoder);
            manager.setJwtAuthenticationConverter(converter(audience));
            byIssuer.put(issuer, manager);
        });
        return new JwtIssuerReactiveAuthenticationManagerResolver(issuer -> Mono.justOrEmpty(byIssuer.get(issuer)));
    }

    /** The only issuers a service believes, each with its audience; a token from any other is refused. */
    static Map<String, Audience> trustedIssuers(String issuerBase, String realmPrefix) {
        String prefix = realmPrefix == null ? "" : realmPrefix;
        Map<String, Audience> issuers = new HashMap<>();
        for (Audience audience : Audience.values()) {
            issuers.put(withSlash(issuerBase) + prefix + audience.realm(), audience);
        }
        return issuers;
    }

    /** The realm's roles as {@code ROLE_} authorities, keeping only those {@code audience} may assert. */
    public static Converter<Jwt, Mono<AbstractAuthenticationToken>> converter(Audience audience) {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> authorities(audience, jwt));
        return new ReactiveJwtAuthenticationConverterAdapter(converter);
    }

    static Collection<GrantedAuthority> authorities(Audience audience, Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        if (roles == null) {
            return List.of();
        }
        return roles.stream()
                .filter(audience::mayAssert)
                .<GrantedAuthority>map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
    }

    private static String withSlash(String base) {
        return base.endsWith("/") ? base : base + "/";
    }
}
