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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
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
import com.techeazyhq.cm.reskit.DependencyUnavailableException;
import com.techeazyhq.cm.reskit.RemoteFailures;
import com.techeazyhq.cm.stf.dom.NewOperator;
import com.techeazyhq.cm.stf.dom.StfAcct;
import com.techeazyhq.cm.stf.dom.StfAcctExistsException;
import com.techeazyhq.cm.stf.dom.StfRole;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;

/** Runs the adapter against a stub of Keycloak's Admin REST API on a real socket. */
class KeycloakStfDirectoryAdapterTest {

    private HttpServer keycloak;
    private final List<String> calls = new CopyOnWriteArrayList<>();
    private volatile Function<HttpExchange, Reply> routes = ex -> new Reply(404, "");

    record Reply(int status, String body, String location) {
        Reply(int status, String body) {
            this(status, body, null);
        }
    }

    @BeforeEach
    void start() throws IOException {
        keycloak = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        keycloak.createContext("/", exchange -> {
            calls.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
            exchange.getRequestBody().readAllBytes();
            Reply reply = routes.apply(exchange);
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

    private KeycloakStfDirectoryAdapter adapter() {
        String base = "http://127.0.0.1:" + keycloak.getAddress().getPort();
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                .recordException(new RemoteFailures()).build());
        Resilience4JConfigurationProperties properties = new Resilience4JConfigurationProperties();
        properties.setDisableTimeLimiter(true);
        return new KeycloakStfDirectoryAdapter(WebClient.builder().baseUrl(base).build(),
                new ReactiveResilience4JCircuitBreakerFactory(registry, TimeLimiterRegistry.ofDefaults(),
                        null /* no bulkhead */, properties), "cm-staff");
    }

    @Test
    void createsTheOperatorRoleIfMissingThenTheUserWithIt() {
        String base = "http://127.0.0.1:" + keycloak.getAddress().getPort();
        boolean[] roleExists = {false};
        routes = ex -> switch (ex.getRequestMethod() + " " + ex.getRequestURI().getPath()) {
            case "GET /admin/realms/cm-staff/roles/CM_OPERATOR" -> roleExists[0]
                    ? new Reply(200, "{\"id\":\"role-op\",\"name\":\"CM_OPERATOR\"}")
                    : new Reply(404, "{}");
            case "POST /admin/realms/cm-staff/roles" -> {
                roleExists[0] = true;
                yield new Reply(201, "");
            }
            case "POST /admin/realms/cm-staff/users" ->
                    new Reply(201, "", base + "/admin/realms/cm-staff/users/u-123");
            case "POST /admin/realms/cm-staff/users/u-123/role-mappings/realm" -> new Reply(204, "");
            default -> new Reply(500, "{}");
        };

        StfAcct created = adapter().createOperator(new NewOperator("dee@example.com", "Dee")).block();

        assertEquals("u-123", created.id());
        assertEquals(StfRole.CM_OPERATOR, created.role());
        assertEquals(List.of(
                "GET /admin/realms/cm-staff/roles/CM_OPERATOR",
                "POST /admin/realms/cm-staff/roles",
                "GET /admin/realms/cm-staff/roles/CM_OPERATOR",
                "POST /admin/realms/cm-staff/users",
                "POST /admin/realms/cm-staff/users/u-123/role-mappings/realm"), calls);
    }

    @Test
    void anAddressAlreadyInUseIsReportedAsSuch() {
        routes = ex -> ex.getRequestURI().getPath().endsWith("/roles/CM_OPERATOR")
                ? new Reply(200, "{\"id\":\"role-op\",\"name\":\"CM_OPERATOR\"}")
                : new Reply(409, "{\"errorMessage\":\"User exists with same username\"}");

        assertThrows(StfAcctExistsException.class,
                () -> adapter().createOperator(new NewOperator("dp-demo@cm.local", "Dee")).block());
    }

    @Test
    void aUserWhoseRoleCouldNotBeGivenIsRemovedAgain() {
        String base = "http://127.0.0.1:" + keycloak.getAddress().getPort();
        routes = ex -> switch (ex.getRequestMethod() + " " + ex.getRequestURI().getPath()) {
            case "GET /admin/realms/cm-staff/roles/CM_OPERATOR" -> new Reply(200, "{\"id\":\"r\",\"name\":\"CM_OPERATOR\"}");
            case "POST /admin/realms/cm-staff/users" -> new Reply(201, "", base + "/admin/realms/cm-staff/users/u-9");
            case "DELETE /admin/realms/cm-staff/users/u-9" -> new Reply(204, "");
            default -> new Reply(503, "{}");
        };

        assertThrows(DependencyUnavailableException.class,
                () -> adapter().createOperator(new NewOperator("dee@example.com", "Dee")).block());
        assertTrue(calls.contains("DELETE /admin/realms/cm-staff/users/u-9"));
    }

    @Test
    void listsAdministratorsAndOperatorsOnceEach() {
        routes = ex -> switch (ex.getRequestURI().getPath()) {
            case "/admin/realms/cm-staff/roles/CM_ADMIN/users" -> new Reply(200,
                    "[{\"id\":\"a1\",\"email\":\"admin@cm.local\",\"firstName\":\"Ada\",\"enabled\":true,\"createdTimestamp\":1790000000000}]");
            case "/admin/realms/cm-staff/roles/CM_OPERATOR/users" -> new Reply(200,
                    "[{\"id\":\"a1\",\"email\":\"admin@cm.local\",\"enabled\":true},"
                            + "{\"id\":\"o1\",\"email\":\"op@cm.local\",\"firstName\":\"Cy\",\"lastName\":\"Das\",\"enabled\":false}]");
            default -> new Reply(500, "{}");
        };

        List<StfAcct> staff = adapter().listStaff().collectList().block();

        assertEquals(2, staff.size());
        assertEquals(StfRole.CM_ADMIN, staff.get(0).role());
        assertEquals("Cy Das", staff.get(1).name());
        assertFalse(staff.get(1).enabled());
    }

    @Test
    void disablingSignsTheAccountOut() {
        routes = ex -> new Reply(204, "");

        adapter().setEnabled("o1", false).block();

        assertEquals(List.of("PUT /admin/realms/cm-staff/users/o1", "POST /admin/realms/cm-staff/users/o1/logout"), calls);
    }
}
