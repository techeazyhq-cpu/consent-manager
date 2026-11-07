package com.techeazyhq.cm.dsr.infra;

import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;

import com.techeazyhq.cm.dsr.dom.DpErasurePorts;

import reactor.core.publisher.Mono;

/**
 * Deletes the principal's account in the data principals' realm (ADR-035), which ends every session they have. A
 * principal's id is their Keycloak user id. One already gone (404) is done: the step is run again after a crash.
 */
public class KeycloakDpIdentityAdapter implements DpErasurePorts.Identity {

    private final WebClient admin;
    private final String realm;

    public KeycloakDpIdentityAdapter(WebClient admin, String realm) {
        this.admin = admin;
        this.realm = realm;
    }

    @Override
    public Mono<Void> delete(String dpId) {
        return admin.delete()
                .uri("/admin/realms/{realm}/users/{id}", realm, dpId)
                .exchangeToMono(resp -> resp.statusCode().is2xxSuccessful() || resp.statusCode() == HttpStatus.NOT_FOUND
                        ? resp.releaseBody()
                        : resp.createError());
    }
}
