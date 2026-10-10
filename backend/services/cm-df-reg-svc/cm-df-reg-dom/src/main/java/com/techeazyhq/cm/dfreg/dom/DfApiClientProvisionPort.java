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
