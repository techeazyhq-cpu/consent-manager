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

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.techeazyhq.cm.dfreg.dom.DfMember;
import com.techeazyhq.cm.dfreg.dom.DfMemberDirectoryPort;
import com.techeazyhq.cm.dfreg.dom.DfMemberExistsException;
import com.techeazyhq.cm.dfreg.dom.DfRole;
import com.techeazyhq.cm.dfreg.dom.NewDfMember;
import com.techeazyhq.cm.reskit.DependencyFallbacks;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * {@link DfMemberDirectoryPort} on Keycloak's Admin REST API (ADR-033): a member is a realm user with the {@code DF}
 * role, one {@code DF_<ROLE>} role and a {@code df_id} attribute that the {@code cm-df-app} client puts in its tokens.
 * Every call goes through the {@code keycloak-admin} circuit breaker (ADR-016); with Keycloak unreachable, team
 * management answers 503.
 * <p>
 * What the realm needs is set up on first use, as {@code --import-realm} never re-imports an existing realm: the
 * {@code DF_*} roles, {@code df_id} declared in the user profile (viewed and edited by administrators only, so a
 * member can't move themselves to another fiduciary), and the {@code df_id} mapper on {@code cm-df-app}.
 */
public class KeycloakDfMemberDirectoryAdapter implements DfMemberDirectoryPort {

    static final String DF_ID = "df_id";
    static final String DF_CONSOLE_CLIENT = "cm-df-app";
    private static final String DF_ROLE = "DF";
    private static final int MAX_MEMBERS = 1000;

    private final WebClient admin;
    private final ReactiveCircuitBreaker circuitBreaker;
    private final String realm;
    private volatile boolean realmReady;

    /** @param adminWebClient based at Keycloak's URL, with the admin token filter ({@code cm-kc-kit}) */
    public KeycloakDfMemberDirectoryAdapter(WebClient adminWebClient, ReactiveCircuitBreakerFactory<?, ?> cbFactory,
                                            String realm) {
        this.admin = adminWebClient;
        this.circuitBreaker = cbFactory.create("keycloak-admin");
        this.realm = realm;
    }

    @Override
    public Mono<DfMember> invite(String dfId, NewDfMember member) {
        Mono<DfMember> create = ensureRealmSetup()
                .then(Mono.zip(role(DF_ROLE), role(member.role().keycloakRole())))
                .flatMap(roles -> admin.post()
                        .uri("/admin/realms/{realm}/users", realm)
                        .bodyValue(Map.of(
                                "username", member.email(),
                                "email", member.email(),
                                "firstName", member.name(),
                                "enabled", true,
                                // the address is proven at every sign-in, by the one-time code sent to it
                                "emailVerified", true,
                                "attributes", Map.of(DF_ID, List.of(dfId))))
                        .retrieve()
                        .toBodilessEntity()
                        .onErrorMap(WebClientResponseException.Conflict.class,
                                e -> new DfMemberExistsException(member.email()))
                        .map(resp -> idFrom(resp.getHeaders().getLocation()))
                        .flatMap(id -> addRoles(id, List.of(roles.getT1(), roles.getT2()))
                                // never leave an account without its roles behind: a retry would then find it taken
                                .onErrorResume(e -> deleteUser(id).onErrorResume(ignored -> Mono.empty())
                                        .then(Mono.error(e)))
                                .thenReturn(new DfMember(id, dfId, member.email(), member.name(), member.role()))));
        return circuitBreaker.run(create, DependencyFallbacks.unavailable("keycloak"));
    }

    @Override
    public Flux<DfMember> list(String dfId) {
        Flux<DfMember> members = admin.get()
                .uri(b -> b.path("/admin/realms/{realm}/users")
                        .queryParam("q", DF_ID + ":" + dfId)
                        .queryParam("briefRepresentation", false)
                        .queryParam("max", MAX_MEMBERS)
                        .build(realm))
                .retrieve()
                .bodyToFlux(UserRep.class)
                // the search is the index; the attribute itself is the truth
                .filter(user -> user.belongsTo(dfId))
                .concatMap(user -> memberRoleOf(user.id()).map(role -> user.toMember(dfId, role)));
        return circuitBreaker.run(members, DependencyFallbacks.unavailableMany("keycloak"));
    }

    @Override
    public Mono<DfMember> find(String dfId, String memberId) {
        Mono<DfMember> find = admin.get()
                .uri("/admin/realms/{realm}/users/{id}", realm, memberId)
                .retrieve()
                .bodyToMono(UserRep.class)
                .onErrorResume(WebClientResponseException.NotFound.class, e -> Mono.empty())
                .filter(user -> user.belongsTo(dfId))
                .flatMap(user -> memberRoleOf(user.id()).map(role -> user.toMember(dfId, role)));
        return circuitBreaker.run(find, DependencyFallbacks.unavailable("keycloak"));
    }

    /** Grants the new role before taking the old one, then signs the member out so the change applies at once. */
    @Override
    public Mono<DfMember> changeRole(DfMember member, DfRole newRole) {
        if (member.role() == newRole) {
            return Mono.just(member);
        }
        Mono<DfMember> change = ensureRealmSetup()
                .then(Mono.zip(role(newRole.keycloakRole()), role(member.role().keycloakRole())))
                .flatMap(roles -> addRoles(member.id(), List.of(roles.getT1()))
                        .then(admin.method(HttpMethod.DELETE)
                                .uri("/admin/realms/{realm}/users/{id}/role-mappings/realm", realm, member.id())
                                .bodyValue(List.of(roles.getT2().asMapping()))
                                .retrieve()
                                .toBodilessEntity())
                        .then(logout(member.id())))
                .thenReturn(new DfMember(member.id(), member.dfId(), member.email(), member.name(), newRole));
        return circuitBreaker.run(change, DependencyFallbacks.unavailable("keycloak"));
    }

    /** Deleting the account ends its sessions too. */
    @Override
    public Mono<Void> remove(DfMember member) {
        return circuitBreaker.run(deleteUser(member.id()), DependencyFallbacks.unavailable("keycloak"));
    }

    /** Idempotent, so safe at every start and on first use; once it has succeeded it isn't repeated. */
    public Mono<Void> ensureRealmSetup() {
        return Mono.defer(() -> realmReady ? Mono.<Void>empty()
                : Flux.fromArray(DfRole.values())
                        .concatMap(role -> ensureRole(role.keycloakRole(), describe(role)))
                        .then(ensureDfIdAttribute())
                        .then(ensureDfIdMapper())
                        .doOnSuccess(done -> realmReady = true));
    }

    private Mono<Void> ensureRole(String name, String description) {
        return admin.get()
                .uri("/admin/realms/{realm}/roles/{role}", realm, name)
                .retrieve()
                .toBodilessEntity()
                .then()
                .onErrorResume(WebClientResponseException.NotFound.class, e -> admin.post()
                        .uri("/admin/realms/{realm}/roles", realm)
                        .bodyValue(Map.of("name", name, "description", description))
                        .retrieve()
                        .toBodilessEntity()
                        .then()
                        .onErrorResume(WebClientResponseException.Conflict.class, conflict -> Mono.empty()));
    }

    /** Keycloak 24+ drops attributes its user profile doesn't declare, so {@code df_id} must be declared. */
    @SuppressWarnings("unchecked")
    private Mono<Void> ensureDfIdAttribute() {
        return admin.get()
                .uri("/admin/realms/{realm}/users/profile", realm)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
                })
                .flatMap(profile -> {
                    List<Map<String, Object>> attributes = new ArrayList<>(
                            (List<Map<String, Object>>) profile.getOrDefault("attributes", List.of()));
                    if (attributes.stream().anyMatch(a -> DF_ID.equals(a.get("name")))) {
                        return Mono.empty();
                    }
                    attributes.add(Map.of(
                            "name", DF_ID,
                            "displayName", "Data fiduciary",
                            "permissions", Map.of("view", List.of("admin"), "edit", List.of("admin")),
                            "multivalued", false));
                    Map<String, Object> updated = new HashMap<>(profile);
                    updated.put("attributes", attributes);
                    return admin.put()
                            .uri("/admin/realms/{realm}/users/profile", realm)
                            .bodyValue(updated)
                            .retrieve()
                            .toBodilessEntity()
                            .then();
                });
    }

    private Mono<Void> ensureDfIdMapper() {
        return admin.get()
                .uri(b -> b.path("/admin/realms/{realm}/clients").queryParam("clientId", DF_CONSOLE_CLIENT).build(realm))
                .retrieve()
                .bodyToFlux(ClientRep.class)
                .next()
                .switchIfEmpty(Mono.error(new IllegalStateException("The realm has no " + DF_CONSOLE_CLIENT + " client")))
                .flatMap(client -> admin.get()
                        .uri("/admin/realms/{realm}/clients/{id}/protocol-mappers/models", realm, client.id())
                        .retrieve()
                        .bodyToFlux(MapperRep.class)
                        .any(mapper -> DF_ID.equals(mapper.name()))
                        .flatMap(present -> present ? Mono.<Void>empty() : admin.post()
                                .uri("/admin/realms/{realm}/clients/{id}/protocol-mappers/models", realm, client.id())
                                .bodyValue(dfIdMapper())
                                .retrieve()
                                .toBodilessEntity()
                                .then()
                                .onErrorResume(WebClientResponseException.Conflict.class, e -> Mono.empty())));
    }

    static Map<String, Object> dfIdMapper() {
        return Map.of(
                "name", DF_ID,
                "protocol", "openid-connect",
                "protocolMapper", "oidc-usermodel-attribute-mapper",
                "config", Map.of(
                        "user.attribute", DF_ID,
                        "claim.name", DF_ID,
                        "jsonType.label", "String",
                        "access.token.claim", "true",
                        "id.token.claim", "true",
                        "userinfo.token.claim", "true"));
    }

    private Mono<RoleRep> role(String name) {
        return admin.get()
                .uri("/admin/realms/{realm}/roles/{role}", realm, name)
                .retrieve()
                .bodyToMono(RoleRep.class);
    }

    private Mono<Void> addRoles(String userId, List<RoleRep> roles) {
        return admin.post()
                .uri("/admin/realms/{realm}/users/{id}/role-mappings/realm", realm, userId)
                .bodyValue(roles.stream().map(RoleRep::asMapping).toList())
                .retrieve()
                .toBodilessEntity()
                .then();
    }

    private Mono<DfRole> memberRoleOf(String userId) {
        return admin.get()
                .uri("/admin/realms/{realm}/users/{id}/role-mappings/realm", realm, userId)
                .retrieve()
                .bodyToFlux(RoleRep.class)
                .map(RoleRep::name)
                .collectList()
                .flatMap(names -> Mono.justOrEmpty(DfRole.fromRoles(names)));
    }

    private Mono<Void> deleteUser(String userId) {
        return admin.delete().uri("/admin/realms/{realm}/users/{id}", realm, userId).retrieve().toBodilessEntity()
                .then()
                .onErrorResume(WebClientResponseException.NotFound.class, e -> Mono.empty());
    }

    private Mono<Void> logout(String userId) {
        return admin.post().uri("/admin/realms/{realm}/users/{id}/logout", realm, userId).retrieve().toBodilessEntity()
                .then();
    }

    private static String describe(DfRole role) {
        return switch (role) {
            case OWNER -> "Fiduciary owner: everything, including ownership";
            case ADMIN -> "Fiduciary administrator: the team, API clients, webhooks and the organisation";
            case CONSENT_MANAGER -> "Fiduciary consent manager: purposes, notices and consent requests";
            case DEVELOPER -> "Fiduciary developer: API clients and webhooks in the sandbox";
            case AUDITOR -> "Fiduciary auditor: read-only";
        };
    }

    private static String idFrom(URI location) {
        if (location == null) {
            throw new IllegalStateException("Keycloak created the user without saying where");
        }
        String path = location.getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    record UserRep(String id, String email, String firstName, String lastName, Map<String, List<String>> attributes) {

        boolean belongsTo(String dfId) {
            return attributes != null && attributes.getOrDefault(DF_ID, List.of()).contains(dfId);
        }

        DfMember toMember(String dfId, DfRole role) {
            String name = String.join(" ", Optional.ofNullable(firstName).orElse(""),
                    Optional.ofNullable(lastName).orElse("")).trim();
            return new DfMember(id, dfId, email, name.isEmpty() ? email : name, role);
        }
    }

    record RoleRep(String id, String name) {

        Map<String, String> asMapping() {
            return Map.of("id", id, "name", name);
        }
    }

    record ClientRep(String id, String clientId) {
    }

    record MapperRep(String id, String name) {
    }
}
