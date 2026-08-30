package com.techeazyhq.cm.dfreg.infra;

import java.util.List;
import java.util.Map;

import org.springframework.web.reactive.function.client.WebClient;

import com.techeazyhq.cm.dfreg.dom.DfApiClientProvisionPort;

import reactor.core.publisher.Mono;

/**
 * {@link DfApiClientProvisionPort} adapter against Keycloak's Admin REST API (plan §10):
 * on activation, creates a dedicated confidential client {@code df-api-<dfId>} with a
 * hardcoded-claim mapper emitting {@code df_id}, and returns its generated secret. This is
 * the piece most likely to need adjustment against a real Keycloak instance during
 * end-to-end verification — the exact Admin REST payload shapes are version-sensitive.
 */
public class KeycloakDfApiClientProvisionAdapter implements DfApiClientProvisionPort {

    private final WebClient adminWebClient;
    private final String realm;

    public KeycloakDfApiClientProvisionAdapter(WebClient adminWebClient, String realm) {
        this.adminWebClient = adminWebClient;
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

        return adminWebClient.post()
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
    }

    private record ClientSecretResp(String type, String value) {
    }
}
