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

package com.techeazyhq.cm.dfreg.infra;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;

/**
 * Attaches a bearer token to every Keycloak Admin API call (plan §10) — obtained via the
 * password grant against the {@code master} realm's {@code admin-cli} client, using the
 * bootstrap admin credentials docker-compose provisions Keycloak with. Cached until near
 * expiry. Fine for Phase 1 local dev; a dedicated service-account client with a scoped
 * {@code manage-clients} role is the Phase 2 hardening (plan §16).
 */
public class KeycloakAdminAuthFilter {

    private final WebClient tokenWebClient;
    private final String adminUsername;
    private final String adminPassword;
    private final AtomicReference<CachedToken> cached = new AtomicReference<>();

    public KeycloakAdminAuthFilter(WebClient.Builder builder, String keycloakBaseUrl, String adminUsername, String adminPassword) {
        this.tokenWebClient = builder.baseUrl(keycloakBaseUrl).build();
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    public ExchangeFilterFunction filter() {
        return ExchangeFilterFunction.ofRequestProcessor(this::attachToken);
    }

    private Mono<ClientRequest> attachToken(ClientRequest request) {
        return currentToken().map(token -> ClientRequest.from(request)
                .headers(headers -> headers.setBearerAuth(token))
                .build());
    }

    private Mono<String> currentToken() {
        CachedToken existing = cached.get();
        if (existing != null && existing.expiresAt().isAfter(Instant.now())) {
            return Mono.just(existing.value());
        }
        return tokenWebClient.post()
                .uri("/realms/master/protocol/openid-connect/token")
                .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue("grant_type=password&client_id=admin-cli&username=" + adminUsername + "&password=" + adminPassword)
                .retrieve()
                .bodyToMono(TokenResp.class)
                .map(resp -> {
                    CachedToken token = new CachedToken(resp.accessToken(), Instant.now().plusSeconds(Math.max(10, resp.expiresIn() - 15)));
                    cached.set(token);
                    return token.value();
                });
    }

    private record CachedToken(String value, Instant expiresAt) {
    }

    private record TokenResp(String access_token, long expires_in) {
        String accessToken() {
            return access_token;
        }

        long expiresIn() {
            return expires_in;
        }
    }
}
