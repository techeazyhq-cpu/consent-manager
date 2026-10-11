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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigurationProperties;
import org.springframework.web.reactive.function.client.WebClient;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.techeazyhq.cm.dfreg.dom.DfMember;
import com.techeazyhq.cm.dfreg.dom.DfMemberExistsException;
import com.techeazyhq.cm.dfreg.dom.DfRole;
import com.techeazyhq.cm.dfreg.dom.NewDfMember;
import com.techeazyhq.cm.reskit.DependencyUnavailableException;
import com.techeazyhq.cm.reskit.RemoteFailures;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;

/** Runs the adapter against a stub of Keycloak's Admin REST API on a real socket. */
class KeycloakDfMemberDirectoryAdapterTest {

    private static final String REALM = "/admin/realms/cm-realm";
    private static final String READY_PROFILE = "{\"attributes\":[{\"name\":\"email\"},{\"name\":\"df_id\"}]}";

    private HttpServer keycloak;
    private final List<String> calls = new CopyOnWriteArrayList<>();
    private final Map<String, String> bodies = new ConcurrentHashMap<>();
    private volatile Function<String, Reply> routes = call -> new Reply(404, "");

    record Reply(int status, String body, String location) {
        Reply(int status, String body) {
            this(status, body, null);
        }
    }

    @BeforeEach
    void start() throws IOException {
        keycloak = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        keycloak.createContext("/", exchange -> {
            String call = callOf(exchange);
            calls.add(call);
            bodies.put(call, new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            Reply reply = routes.apply(call);
            byte[] body = reply.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            if (reply.location() != null) {
                exchange.getResponseHeaders().set("Location", reply.location());
            }
            exchange.sendResponseHeaders(reply.status(), body.length == 0 ? -1 : body.length);
            if (body.length > 0) {
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });
        keycloak.start();
    }

    @AfterEach
    void stop() {
        keycloak.stop(0);
    }

    private static String callOf(HttpExchange exchange) {
        String query = exchange.getRequestURI().getQuery();
        return exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath() + (query == null ? "" : "?" + query);
    }

    private String base() {
        return "http://127.0.0.1:" + keycloak.getAddress().getPort();
    }

    private KeycloakDfMemberDirectoryAdapter adapter() {
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                .recordException(new RemoteFailures()).build());
        Resilience4JConfigurationProperties properties = new Resilience4JConfigurationProperties();
        properties.setDisableTimeLimiter(true);
        return new KeycloakDfMemberDirectoryAdapter(WebClient.builder().baseUrl(base()).build(),
                new ReactiveResilience4JCircuitBreakerFactory(registry, TimeLimiterRegistry.ofDefaults(),
                        null /* no bulkhead */, properties), "cm-realm");
    }

    /** A realm already set up: every role, the attribute and the mapper exist. */
    private static Reply readyRealm(String call) {
        if (call.startsWith("GET " + REALM + "/roles/")) {
            String name = call.substring(call.lastIndexOf('/') + 1);
            return new Reply(200, "{\"id\":\"r-" + name + "\",\"name\":\"" + name + "\"}");
        }
        return switch (call) {
            case "GET " + REALM + "/users/profile" -> new Reply(200, READY_PROFILE);
            case "GET " + REALM + "/clients?clientId=cm-df-app" -> new Reply(200, "[{\"id\":\"c-1\",\"clientId\":\"cm-df-app\"}]");
            case "GET " + REALM + "/clients/c-1/protocol-mappers/models" -> new Reply(200, "[{\"id\":\"m\",\"name\":\"df_id\"}]");
            default -> null;
        };
    }

    @Test
    void invitesAPersonWithTheirFiduciaryAndRole() {
        routes = call -> {
            Reply ready = readyRealm(call);
            if (ready != null) {
                return ready;
            }
            return switch (call) {
                case "POST " + REALM + "/users" -> new Reply(201, "", base() + REALM + "/users/u-1");
                case "POST " + REALM + "/users/u-1/role-mappings/realm" -> new Reply(204, "");
                default -> new Reply(500, "{}");
            };
        };

        DfMember member = adapter().invite("df-1", new NewDfMember("cy@acme.example", "Cy", DfRole.ADMIN)).block();

        assertEquals(new DfMember("u-1", "df-1", "cy@acme.example", "Cy", DfRole.ADMIN), member);
        assertTrue(bodies.get("POST " + REALM + "/users").contains("\"df_id\":[\"df-1\"]"));
        String roles = bodies.get("POST " + REALM + "/users/u-1/role-mappings/realm");
        assertTrue(roles.contains("\"DF\"") && roles.contains("\"DF_ADMIN\""), roles);
    }

    @Test
    void setsUpWhatTheRealmLacksOnce() {
        boolean[] made = {false};
        routes = call -> {
            if (call.startsWith("GET " + REALM + "/roles/DF_")) {
                return made[0] ? readyRealm(call) : new Reply(404, "{}");
            }
            return switch (call) {
                case "POST " + REALM + "/roles" -> new Reply(201, "");
                case "GET " + REALM + "/users/profile" -> new Reply(200, "{\"attributes\":[{\"name\":\"email\"}],\"groups\":[]}");
                case "PUT " + REALM + "/users/profile" -> new Reply(200, READY_PROFILE);
                case "GET " + REALM + "/clients?clientId=cm-df-app" -> new Reply(200, "[{\"id\":\"c-1\"}]");
                case "GET " + REALM + "/clients/c-1/protocol-mappers/models" -> new Reply(200, "[]");
                case "POST " + REALM + "/clients/c-1/protocol-mappers/models" -> {
                    made[0] = true;
                    yield new Reply(201, "");
                }
                default -> new Reply(500, "{}");
            };
        };
        KeycloakDfMemberDirectoryAdapter adapter = adapter();

        adapter.ensureRealmSetup().block();
        adapter.ensureRealmSetup().block();

        assertEquals(DfRole.values().length, calls.stream().filter(c -> c.equals("POST " + REALM + "/roles")).count());
        String profile = bodies.get("PUT " + REALM + "/users/profile");
        assertTrue(profile.contains("\"name\":\"df_id\"") && profile.contains("\"groups\":[]"), profile);
        assertTrue(profile.contains("\"edit\":[\"admin\"]"), profile);
        assertTrue(bodies.get("POST " + REALM + "/clients/c-1/protocol-mappers/models")
                .contains("oidc-usermodel-attribute-mapper"));
        assertEquals(1, calls.stream().filter(c -> c.equals("GET " + REALM + "/users/profile")).count());
    }

    @Test
    void anAddressAlreadyInUseIsReportedAsSuch() {
        routes = call -> {
            Reply ready = readyRealm(call);
            return ready != null ? ready : new Reply(409, "{\"errorMessage\":\"User exists with same username\"}");
        };

        assertThrows(DfMemberExistsException.class, () -> adapter()
                .invite("df-1", new NewDfMember("operator@cm.local", "Op", DfRole.AUDITOR)).block());
    }

    @Test
    void aPersonWhoseRolesCouldNotBeGivenIsRemovedAgain() {
        routes = call -> {
            Reply ready = readyRealm(call);
            if (ready != null) {
                return ready;
            }
            return switch (call) {
                case "POST " + REALM + "/users" -> new Reply(201, "", base() + REALM + "/users/u-9");
                case "DELETE " + REALM + "/users/u-9" -> new Reply(204, "");
                default -> new Reply(503, "{}");
            };
        };

        assertThrows(DependencyUnavailableException.class, () -> adapter()
                .invite("df-1", new NewDfMember("cy@acme.example", "Cy", DfRole.ADMIN)).block());
        assertTrue(calls.contains("DELETE " + REALM + "/users/u-9"));
    }

    @Test
    void listsOnlyThisFiduciarysPeopleWithTheirRoles() {
        routes = call -> {
            if (call.startsWith("GET " + REALM + "/users?")) {
                return new Reply(200, "[{\"id\":\"u-1\",\"email\":\"bo@acme.example\",\"firstName\":\"Bo\","
                        + "\"attributes\":{\"df_id\":[\"df-1\"]}},"
                        + "{\"id\":\"u-2\",\"email\":\"x@other.example\",\"attributes\":{\"df_id\":[\"df-10\"]}},"
                        + "{\"id\":\"u-3\",\"email\":\"api@acme.example\",\"attributes\":{\"df_id\":[\"df-1\"]}}]");
            }
            return switch (call) {
                case "GET " + REALM + "/users/u-1/role-mappings/realm" ->
                        new Reply(200, "[{\"id\":\"a\",\"name\":\"DF\"},{\"id\":\"b\",\"name\":\"DF_OWNER\"}]");
                // no member role: not a member, whatever its attribute says
                case "GET " + REALM + "/users/u-3/role-mappings/realm" -> new Reply(200, "[{\"id\":\"a\",\"name\":\"DF\"}]");
                default -> new Reply(500, "{}");
            };
        };

        List<DfMember> members = adapter().list("df-1").collectList().block();

        assertEquals(List.of(new DfMember("u-1", "df-1", "bo@acme.example", "Bo", DfRole.OWNER)), members);
        assertTrue(calls.get(0).contains("q=df_id:df-1"), calls.get(0));
    }

    @Test
    void someoneElsesMemberIsNotFound() {
        routes = call -> call.equals("GET " + REALM + "/users/u-2")
                ? new Reply(200, "{\"id\":\"u-2\",\"attributes\":{\"df_id\":[\"df-2\"]}}")
                : new Reply(500, "{}");

        assertEquals(null, adapter().find("df-1", "u-2").block());
    }

    @Test
    void changingARoleGrantsTheNewOneFirstThenSignsTheMemberOut() {
        routes = call -> {
            Reply ready = readyRealm(call);
            return ready != null ? ready : new Reply(204, "");
        };
        DfMember member = new DfMember("u-1", "df-1", "cy@acme.example", "Cy", DfRole.ADMIN);

        DfMember changed = adapter().changeRole(member, DfRole.AUDITOR).block();

        assertEquals(DfRole.AUDITOR, changed.role());
        List<String> userCalls = calls.stream().filter(c -> c.contains("/users/u-1")).toList();
        assertEquals(List.of("POST " + REALM + "/users/u-1/role-mappings/realm",
                "DELETE " + REALM + "/users/u-1/role-mappings/realm",
                "POST " + REALM + "/users/u-1/logout"), userCalls);
        assertTrue(bodies.get("DELETE " + REALM + "/users/u-1/role-mappings/realm").contains("DF_ADMIN"));
    }
}
