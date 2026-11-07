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
