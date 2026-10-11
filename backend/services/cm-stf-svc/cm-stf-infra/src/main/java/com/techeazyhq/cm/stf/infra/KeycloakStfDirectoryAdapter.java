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

package com.techeazyhq.cm.stf.infra;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.techeazyhq.cm.reskit.DependencyFallbacks;
import com.techeazyhq.cm.stf.dom.NewOperator;
import com.techeazyhq.cm.stf.dom.StfAcct;
import com.techeazyhq.cm.stf.dom.StfAcctExistsException;
import com.techeazyhq.cm.stf.dom.StfDirectoryPort;
import com.techeazyhq.cm.stf.dom.StfRole;
import com.techeazyhq.cm.stf.dom.StfSession;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * {@link StfDirectoryPort} on Keycloak's Admin REST API: a member of staff is a realm user with the
 * {@code CM_ADMIN} or {@code CM_OPERATOR} realm role. Every call goes through the {@code keycloak-admin} circuit
 * breaker (ADR-016); with Keycloak unreachable, staff management answers 503.
 * <p>
 * The {@code CM_OPERATOR} role is created on first use when the realm lacks it: a realm imported before the role
 * existed is never re-imported ({@code --import-realm} skips an existing realm).
 */
public class KeycloakStfDirectoryAdapter implements StfDirectoryPort {

    private static final int MAX_STAFF = 1000;

    private final WebClient admin;
    private final ReactiveCircuitBreaker circuitBreaker;
    private final String realm;

    /** @param adminWebClient based at Keycloak's URL, with the admin token filter ({@code cm-kc-kit}) */
    public KeycloakStfDirectoryAdapter(WebClient adminWebClient, ReactiveCircuitBreakerFactory<?, ?> cbFactory,
                                       String realm) {
        this.admin = adminWebClient;
        this.circuitBreaker = cbFactory.create("keycloak-admin");
        this.realm = realm;
    }

    @Override
    public Mono<StfAcct> createOperator(NewOperator operator) {
        Mono<StfAcct> create = operatorRole()
                .flatMap(role -> admin.post()
                        .uri("/admin/realms/{realm}/users", realm)
                        .bodyValue(Map.of(
                                "username", operator.email(),
                                "email", operator.email(),
                                "firstName", operator.name(),
                                "enabled", true,
                                // the address is proven at every sign-in, by the one-time code sent to it
                                "emailVerified", true))
                        .retrieve()
                        .toBodilessEntity()
                        .onErrorMap(WebClientResponseException.Conflict.class,
                                e -> new StfAcctExistsException(operator.email()))
                        .map(resp -> idFrom(resp.getHeaders().getLocation()))
                        .flatMap(id -> assignRole(id, role)
                                // never leave an account without its role behind: a retry would then find it taken
                                .onErrorResume(e -> deleteUser(id).then(Mono.error(e)))
                                .thenReturn(new StfAcct(id, operator.email(), operator.name(), StfRole.CM_OPERATOR, true,
                                        Instant.now()))));
        return circuitBreaker.run(create, DependencyFallbacks.unavailable("keycloak"));
    }

    @Override
    public Flux<StfAcct> listStaff() {
        Flux<StfAcct> staff = Flux.just(StfRole.CM_ADMIN, StfRole.CM_OPERATOR)
                .concatMap(role -> admin.get()
                        .uri("/admin/realms/{realm}/roles/{role}/users?first=0&max={max}", realm, role.name(), MAX_STAFF)
                        .retrieve()
                        .bodyToFlux(UserRep.class)
                        .onErrorResume(WebClientResponseException.NotFound.class, e -> Flux.empty())
                        .map(user -> user.toAcct(role)))
                // someone holding both roles is listed once, as an administrator
                .distinct(StfAcct::id);
        return circuitBreaker.run(staff, DependencyFallbacks.unavailableMany("keycloak"));
    }

    @Override
    public Mono<StfAcct> findStaff(String id) {
        Mono<StfAcct> find = admin.get()
                .uri("/admin/realms/{realm}/users/{id}", realm, id)
                .retrieve()
                .bodyToMono(UserRep.class)
                .onErrorResume(WebClientResponseException.NotFound.class, e -> Mono.empty())
                .flatMap(user -> staffRoleOf(id).map(role -> user.toAcct(role)));
        return circuitBreaker.run(find, DependencyFallbacks.unavailable("keycloak"));
    }

    @Override
    public Mono<Void> setEnabled(String id, boolean enabled) {
        Mono<Void> update = admin.put()
                .uri("/admin/realms/{realm}/users/{id}", realm, id)
                .bodyValue(Map.of("enabled", enabled))
                .retrieve()
                .toBodilessEntity()
                .then(enabled ? Mono.empty() : logout(id));
        return circuitBreaker.run(update, DependencyFallbacks.unavailable("keycloak"));
    }

    @Override
    public Flux<StfSession> sessionsOf(String id) {
        Flux<StfSession> sessions = admin.get()
                .uri("/admin/realms/{realm}/users/{id}/sessions", realm, id)
                .retrieve()
                .bodyToFlux(SessionRep.class)
                .map(SessionRep::toSession);
        return circuitBreaker.run(sessions, DependencyFallbacks.unavailableMany("keycloak"));
    }

    @Override
    public Mono<Void> signOutEverywhere(String id) {
        return circuitBreaker.run(logout(id), DependencyFallbacks.unavailable("keycloak"));
    }

    private Mono<RoleRep> operatorRole() {
        Mono<RoleRep> find = admin.get()
                .uri("/admin/realms/{realm}/roles/{role}", realm, StfRole.CM_OPERATOR.name())
                .retrieve()
                .bodyToMono(RoleRep.class);
        return find.onErrorResume(WebClientResponseException.NotFound.class, e -> admin.post()
                .uri("/admin/realms/{realm}/roles", realm)
                .bodyValue(Map.of("name", StfRole.CM_OPERATOR.name(),
                        "description", "Platform operator: views tenants and onboards them"))
                .retrieve()
                .toBodilessEntity()
                .onErrorResume(WebClientResponseException.Conflict.class, conflict -> Mono.empty()) // created meanwhile
                .then(find));
    }

    private Mono<Void> assignRole(String userId, RoleRep role) {
        return admin.post()
                .uri("/admin/realms/{realm}/users/{id}/role-mappings/realm", realm, userId)
                .bodyValue(List.of(Map.of("id", role.id(), "name", role.name())))
                .retrieve()
                .toBodilessEntity()
                .then();
    }

    private Mono<StfRole> staffRoleOf(String userId) {
        return admin.get()
                .uri("/admin/realms/{realm}/users/{id}/role-mappings/realm", realm, userId)
                .retrieve()
                .bodyToFlux(RoleRep.class)
                .map(RoleRep::name)
                .collectList()
                .flatMap(names -> Mono.justOrEmpty(names.contains(StfRole.CM_ADMIN.name()) ? StfRole.CM_ADMIN
                        : names.contains(StfRole.CM_OPERATOR.name()) ? StfRole.CM_OPERATOR : null));
    }

    private Mono<Void> deleteUser(String userId) {
        return admin.delete().uri("/admin/realms/{realm}/users/{id}", realm, userId).retrieve().toBodilessEntity()
                .then()
                .onErrorResume(e -> Mono.empty());
    }

    private Mono<Void> logout(String userId) {
        return admin.post().uri("/admin/realms/{realm}/users/{id}/logout", realm, userId).retrieve().toBodilessEntity()
                .then();
    }

    private static String idFrom(URI location) {
        if (location == null) {
            throw new IllegalStateException("Keycloak created the user without saying where");
        }
        String path = location.getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    record UserRep(String id, String email, String firstName, String lastName, Boolean enabled, Long createdTimestamp) {

        StfAcct toAcct(StfRole role) {
            String name = String.join(" ", Optional.ofNullable(firstName).orElse(""),
                    Optional.ofNullable(lastName).orElse("")).trim();
            return new StfAcct(id, email, name.isEmpty() ? email : name, role, !Boolean.FALSE.equals(enabled),
                    createdTimestamp == null ? null : Instant.ofEpochMilli(createdTimestamp));
        }
    }

    record RoleRep(String id, String name) {
    }

    record SessionRep(String id, String ipAddress, Long start, Long lastAccess, Map<String, String> clients) {

        StfSession toSession() {
            return new StfSession(id, ipAddress, start == null ? null : Instant.ofEpochMilli(start),
                    lastAccess == null ? null : Instant.ofEpochMilli(lastAccess),
                    clients == null ? Set.of() : Set.copyOf(clients.values()));
        }
    }
}
