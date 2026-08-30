package com.techeazyhq.cm.dfreg.dom;

import reactor.core.publisher.Mono;

/**
 * Port (plan §10): implemented in -infra against Keycloak's Admin REST API. Provisions the
 * {@code cm-df-api} client-credentials client for a newly activated DF, with a custom mapper
 * emitting {@code df_id} — Phase 1 keeps this simple (shared client pattern per plan §10);
 * Phase 2 hardens to per-DF dedicated clients + secret rotation.
 */
public interface DfApiClientProvisionPort {

    Mono<String> provisionClientSecret(String dfId);
}
