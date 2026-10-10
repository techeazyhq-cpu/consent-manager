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

package com.techeazyhq.cm.ntf.infra;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.techeazyhq.cm.ntf.dom.NtfChnl;
import com.techeazyhq.cm.ntf.dom.NtfCtg;
import com.techeazyhq.cm.ntf.dom.NtfDeliveryRejectedException;
import com.techeazyhq.cm.ntf.dom.NtfMsg;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs the client against a stub of notification-service's {@code POST /v1/notifications} on a real socket. */
class NotificationServiceClientTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private HttpServer server;
    private final List<Recorded> requests = new CopyOnWriteArrayList<>();
    private final Deque<int[]> statuses = new ArrayDeque<>();
    private final Deque<String> bodies = new ArrayDeque<>();

    record Recorded(String path, String apiKey, String idempotencyKey, String signature, String timestamp,
                    String nonce, byte[] body) {
        JsonNode json() throws IOException {
            return JSON.readTree(body);
        }
    }

    @BeforeEach
    void startStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    @AfterEach
    void stopStub() {
        server.stop(0);
    }

    private synchronized void handle(HttpExchange exchange) throws IOException {
        var h = exchange.getRequestHeaders();
        requests.add(new Recorded(exchange.getRequestURI().getRawPath(), h.getFirst("X-API-Key"),
                h.getFirst("Idempotency-Key"), h.getFirst("X-Signature"), h.getFirst("X-Signature-Timestamp"),
                h.getFirst("X-Signature-Nonce"), exchange.getRequestBody().readAllBytes()));
        int status = statuses.isEmpty() ? 202 : statuses.poll()[0];
        byte[] response = (bodies.isEmpty() ? "{\"requestId\":\"00000000-0000-0000-0000-000000000001\"}" : bodies.poll())
                .getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }

    private void respond(int status, String body) {
        statuses.add(new int[] {status});
        bodies.add(body);
    }

    private NotificationServiceClient client(String signingSecret, String fromAddress) {
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
        return new NotificationServiceClient(WebClient.builder(), JSON, new NotificationServiceClient.Settings(
                baseUrl, "ntf_test", signingSecret, fromAddress, 120, Duration.ofSeconds(5), 2, Duration.ofMillis(10)));
    }

    @Test
    void sendsATransactionalEmailWithItsRefAsIdempotencyKeyAndClientReference() throws IOException {
        client(null, "consent@acme.example").send(NtfChnl.EMAIL,
                new NtfMsg("ann@example.com", "New consent request", "Acme asks for consent", NtfCtg.TRANSACTIONAL,
                        "cm-notify-dp-req-1")).block();

        Recorded sent = requests.get(0);
        assertEquals("/v1/notifications", sent.path());
        assertEquals("ntf_test", sent.apiKey());
        assertEquals("cm-notify-dp-req-1", sent.idempotencyKey());
        assertEquals(null, sent.signature());
        JsonNode body = sent.json();
        assertEquals("EMAIL", body.get("channel").asText());
        assertEquals("ann@example.com", body.get("recipient").asText());
        assertEquals("New consent request", body.get("subject").asText());
        assertEquals("Acme asks for consent", body.get("body").asText());
        assertEquals("cm-notify-dp-req-1", body.get("clientReference").asText());
        assertEquals("TRANSACTIONAL", body.get("category").asText());
        assertEquals("consent@acme.example", body.get("from").asText());
        assertFalse(body.has("validitySeconds"));
    }

    @Test
    void sendsAnOtpWithItsValidityAndNoSenderAddressOutsideEmail() throws IOException {
        client(null, "consent@acme.example").send(NtfChnl.SMS,
                new NtfMsg("+919800000001", "Code", "Your code is 123456", NtfCtg.OTP, "cm-otp-1")).block();

        JsonNode body = requests.get(0).json();
        assertEquals("SMS", body.get("channel").asText());
        assertEquals("OTP", body.get("category").asText());
        assertEquals(120, body.get("validitySeconds").asInt());
        assertFalse(body.has("from"));
    }

    @Test
    void signsTheExactBytesItSends() {
        client("nss_secret", null).send(NtfChnl.PUSH, new NtfMsg("device-token", "Title", "Text")).block();

        Recorded sent = requests.get(0);
        String expected = NtfRequestSigner.sign("nss_secret", Long.parseLong(sent.timestamp()), sent.nonce(), "POST",
                "/v1/notifications", null, sent.body());
        assertEquals(expected, sent.signature());
    }

    @Test
    void retriesRateLimitsAndServerErrorsWithTheSameIdempotencyKeyAndAFreshSignature() {
        respond(429, "{\"code\":\"RATE_LIMITED\",\"retryable\":true}");
        respond(503, "");
        client("nss_secret", null).send(NtfChnl.SMS, new NtfMsg("+919800000001", null, "Hi", NtfCtg.TRANSACTIONAL, "cm-r-1"))
                .block();

        assertEquals(3, requests.size());
        assertTrue(requests.stream().allMatch(r -> "cm-r-1".equals(r.idempotencyKey())));
        assertEquals(3, requests.stream().map(Recorded::nonce).distinct().count());
    }

    @Test
    void reportsAFinalRefusalWithoutRetrying() {
        respond(403, "{\"code\":\"CHANNEL_NOT_ALLOWED\",\"message\":\"WHATSAPP is not enabled\",\"retryable\":false}");

        NtfDeliveryRejectedException rejected = assertThrows(NtfDeliveryRejectedException.class, () ->
                client(null, null).send(NtfChnl.WHATSAPP, new NtfMsg("+919800000001", null, "Hi")).block());

        assertEquals("CHANNEL_NOT_ALLOWED", rejected.code());
        assertEquals(1, requests.size());
    }

    @Test
    void givesUpOnTransientFailuresAfterMaxRetriesWithoutCallingThemRejections() {
        for (int i = 0; i < 3; i++) {
            respond(500, "{\"code\":\"INTERNAL_ERROR\",\"retryable\":true}");
        }

        RuntimeException failure = assertThrows(RuntimeException.class, () ->
                client(null, null).send(NtfChnl.SMS, new NtfMsg("+919800000001", null, "Hi")).block());

        assertTrue(failure instanceof NotificationServiceClient.TransientFailure, failure.toString());
        assertEquals(3, requests.size());
    }

    @Test
    void refusesTheAppChannelWhichIsTheDashboardNotADelivery() {
        assertThrows(IllegalArgumentException.class, () ->
                client(null, null).send(NtfChnl.APP, new NtfMsg("dp-1", null, "Hi")).block());
        assertTrue(requests.isEmpty());
    }
}
