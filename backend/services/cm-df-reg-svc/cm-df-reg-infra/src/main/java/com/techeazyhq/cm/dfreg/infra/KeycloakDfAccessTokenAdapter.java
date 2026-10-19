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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import com.techeazyhq.cm.dfreg.dom.DfAccessTokenPort;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Mono;

/**
 * A fiduciary's API client token from its realm, by client credentials (ADR-041), cached until shortly before it
 * expires so a busy key costs Keycloak one request per token lifetime.
 *
 * <p>{@code publicHost}, when set, is sent as the Host header: Keycloak names a token's issuer after the address it
 * was asked at, and services trust only the public one ({@code http://localhost:8080/realms/...} locally). In
 * production Keycloak runs with a fixed hostname, and this stays empty.
 */
public class KeycloakDfAccessTokenAdapter implements DfAccessTokenPort {

    private static final long REFRESH_BEFORE_SECONDS = 30;

    private final WebClient keycloak;
    private final String realm;
    private final String publicHost;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    public KeycloakDfAccessTokenAdapter(WebClient.Builder builder, String baseUrl, String realm, String publicHost) {
        this.keycloak = builder.baseUrl(baseUrl).build();
        this.realm = realm;
        this.publicHost = publicHost == null || publicHost.isBlank() ? null : publicHost.trim();
    }

    @Override
    public Mono<String> tokenFor(String dfId, String clientSecret) {
        Cached cached = cache.get(dfId);
        if (cached != null && cached.secret.equals(clientSecret)
                && cached.expiresAt.isAfter(Instant.now().plusSeconds(REFRESH_BEFORE_SECONDS))) {
            return Mono.just(cached.token);
        }
        return keycloak.post()
                .uri("/realms/{realm}/protocol/openid-connect/token", realm)
                .headers(h -> {
                    if (publicHost != null) {
                        h.set("Host", publicHost);
                    }
                })
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("grant_type", "client_credentials")
                        .with("client_id", "df-api-" + dfId)
                        .with("client_secret", clientSecret == null ? "" : clientSecret))
                .retrieve()
                .bodyToMono(TokenResp.class)
                .map(resp -> {
                    cache.put(dfId, new Cached(resp.access_token(), clientSecret,
                            Instant.now().plusSeconds(resp.expires_in())));
                    return resp.access_token();
                })
                .onErrorMap(e -> !(e instanceof DomRuleViolation), e -> new DomRuleViolation(503, "token-unavailable",
                        "Couldn't get an access token for this fiduciary right now; try again shortly"));
    }

    private record Cached(String token, String secret, Instant expiresAt) {
    }

    private record TokenResp(String access_token, long expires_in) {
    }
}
