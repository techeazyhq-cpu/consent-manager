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

import java.util.List;
import java.util.Map;

import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.web.reactive.function.client.WebClient;

import com.techeazyhq.cm.dfreg.dom.DfApiClientProvisionPort;
import com.techeazyhq.cm.reskit.DependencyFallbacks;

import reactor.core.publisher.Mono;

/**
 * {@link DfApiClientProvisionPort} adapter against Keycloak's Admin REST API (plan §10):
 * on activation, creates a dedicated confidential client {@code df-api-<dfId>} with a
 * hardcoded-claim mapper emitting {@code df_id}, and returns its generated secret. This is
 * the piece most likely to need adjustment against a real Keycloak instance during
 * end-to-end verification — the exact Admin REST payload shapes are version-sensitive.
 * <p>
 * Behind a circuit breaker (ADR-016): with Keycloak unreachable or its breaker open, activation fails with 503 and the
 * fiduciary stays PENDING, so the administrator can activate it again; a refusal from Keycloak (4xx) is passed on.
 */
public class KeycloakDfApiClientProvisionAdapter implements DfApiClientProvisionPort {

    private final WebClient adminWebClient;
    private final ReactiveCircuitBreaker circuitBreaker;
    private final String realm;

    public KeycloakDfApiClientProvisionAdapter(WebClient adminWebClient, ReactiveCircuitBreakerFactory<?, ?> cbFactory,
                                               String realm) {
        this.adminWebClient = adminWebClient;
        this.circuitBreaker = cbFactory.create("keycloak-admin");
        this.realm = realm;
    }

    @Override
    public Mono<String> provisionClientSecret(String dfId) {
        String clientId = "df-api-" + dfId;
        Map<String, Object> clientRepresentation = Map.of(
                "clientId", clientId,
                "protocol", "openid-connect",
                "publicClient", false,
                "serviceAccountsEnabled", true,
                "standardFlowEnabled", false,
                "directAccessGrantsEnabled", false,
                // mf-df-app (plan §10's DF Console) calls this client's token endpoint
                // directly from the browser rather than server-to-server — without webOrigins,
                // Keycloak omits Access-Control-Allow-Origin on the response, so the exchange
                // succeeds server-side but the browser still blocks reading it ("Failed to
                // fetch"). Found only once an actual browser called this, not curl.
                "webOrigins", List.of(
                        "http://localhost:5173", "http://localhost:5174", "http://localhost:5175", "http://localhost:5176"),
                "protocolMappers", List.of(Map.of(
                        "name", "df-id-mapper",
                        "protocol", "openid-connect",
                        "protocolMapper", "oidc-hardcoded-claim-mapper",
                        "config", Map.of(
                                "claim.name", "df_id",
                                "claim.value", dfId,
                                "jsonType.label", "String",
                                "access.token.claim", "true"))));

        Mono<String> provision = adminWebClient.post()
                .uri("/admin/realms/{realm}/clients", realm)
                .bodyValue(clientRepresentation)
                .retrieve()
                .toBodilessEntity()
                .map(resp -> resp.getHeaders().getLocation())
                .flatMap(location -> adminWebClient.get()
                        .uri(location.toString() + "/client-secret")
                        .retrieve()
                        .bodyToMono(ClientSecretResp.class))
                .map(ClientSecretResp::value);
        return circuitBreaker.run(provision, DependencyFallbacks.unavailable("keycloak"));
    }

    private record ClientSecretResp(String type, String value) {
    }
}
