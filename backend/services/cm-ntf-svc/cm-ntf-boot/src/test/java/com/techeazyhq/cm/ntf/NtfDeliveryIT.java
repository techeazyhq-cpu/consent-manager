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

package com.techeazyhq.cm.ntf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeazyhq.cm.ntf.app.NotifyDpCmd;
import com.techeazyhq.cm.ntf.app.NotifyDpCmdHandler;
import com.techeazyhq.cm.ntf.app.NtfDispatchService;
import com.techeazyhq.cm.ntf.app.OtpSendCmd;
import com.techeazyhq.cm.ntf.app.OtpSendCmdHandler;
import com.techeazyhq.cm.ntf.dom.NtfChnl;
import com.techeazyhq.cm.ntf.dom.NtfDeliveryRejectedException;
import com.techeazyhq.cm.ntf.dom.PushPltf;
import com.techeazyhq.cm.ntf.infra.CatcherRouterConfig;
import com.techeazyhq.cm.ntf.infra.CatcherStore;
import com.techeazyhq.cm.ntf.infra.PushCatcherAdapter;
import com.techeazyhq.cm.ntf.infra.R2dbcPushDvcRepoAdapter;
import com.techeazyhq.cm.ntf.infra.SmsCatcherAdapter;
import com.techeazyhq.cm.ntf.infra.SmtpEmailSenderAdapter;
import com.techeazyhq.cm.ntf.infra.WhatsAppCatcherAdapter;
import com.techeazyhq.cm.testkit.PgIntegrationTestBase;

import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import reactor.core.publisher.Mono;

/**
 * What reaches a person (#133), with the local channels the stack runs: email through a real Mailpit, read back from
 * its HTTP API; SMS, WhatsApp and push through the catchers, read back from their {@code /dev/*-catcher} endpoints.
 */
@Testcontainers
class NtfDeliveryIT extends PgIntegrationTestBase {

    @Container
    static final GenericContainer<?> MAILPIT = new GenericContainer<>(DockerImageName.parse("axllent/mailpit:latest"))
            .withExposedPorts(1025, 8025)
            .waitingFor(Wait.forHttp("/livez").forPort(8025));

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private static final List<String> FAILED_DELIVERIES = new CopyOnWriteArrayList<>();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private static R2dbcPushDvcRepoAdapter pushDevices;
    private static NotifyDpCmdHandler notifyDp;
    private static OtpSendCmdHandler otp;
    private static WebTestClient catchers;

    @BeforeAll
    static void setUp() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("ntf").defaultSchema("ntf").locations("classpath:db/migration")
                .load().migrate();
        ConnectionFactory cf = ConnectionFactories.get(ConnectionFactoryOptions.builder()
                .option(ConnectionFactoryOptions.DRIVER, "postgresql")
                .option(ConnectionFactoryOptions.HOST, POSTGRES.getHost())
                .option(ConnectionFactoryOptions.PORT, POSTGRES.getFirstMappedPort())
                .option(ConnectionFactoryOptions.DATABASE, POSTGRES.getDatabaseName())
                .option(ConnectionFactoryOptions.USER, POSTGRES.getUsername())
                .option(ConnectionFactoryOptions.PASSWORD, POSTGRES.getPassword())
                .build());
        pushDevices = new R2dbcPushDvcRepoAdapter(DatabaseClient.create(cf),
                TransactionalOperator.create(new R2dbcTransactionManager(cf)));

        JavaMailSenderImpl mail = new JavaMailSenderImpl();
        mail.setHost(MAILPIT.getHost());
        mail.setPort(MAILPIT.getMappedPort(1025));
        CatcherStore sms = new CatcherStore();
        CatcherStore whatsApp = new CatcherStore();
        CatcherStore push = new CatcherStore();
        NtfDispatchService dispatch = new NtfDispatchService(
                new SmtpEmailSenderAdapter(mail, "no-reply@cm.local"), new SmsCatcherAdapter(sms),
                new WhatsAppCatcherAdapter(whatsApp), new PushCatcherAdapter(push));
        notifyDp = new NotifyDpCmdHandler(dispatch, pushDevices, (cnsReqId, channel, code) ->
                Mono.fromRunnable(() -> FAILED_DELIVERIES.add(cnsReqId + ":" + channel + ":" + code)));
        otp = new OtpSendCmdHandler(dispatch);
        catchers = WebTestClient.bindToRouterFunction(new CatcherRouterConfig().catcherRoutes(sms, whatsApp, push))
                .build();
    }

    @Test
    void aPrincipalAskedByEmailGetsTheNoticeInTheirInbox() throws Exception {
        String to = "ann-" + System.nanoTime() + "@example.com";

        notifyDp.handle(new NotifyDpCmd("req-1", "df-a", "EMAIL", to, "Order updates",
                "Acme asks to email you about your orders.")).block();

        JsonNode message = awaitMail(to);
        assertEquals("Order updates", message.get("Subject").asText());
        assertEquals("no-reply@cm.local", message.get("From").get("Address").asText());
        JsonNode full = getJson("/api/v1/message/" + message.get("ID").asText());
        assertTrue(full.get("Text").asText().contains("Acme asks to email you about your orders."));
    }

    @Test
    void aPrincipalAskedByPhoneGetsAnSmsInTheCatcher() {
        String to = "+9198" + (System.nanoTime() % 100_000_000L);

        notifyDp.handle(new NotifyDpCmd("req-2", "df-a", "PHONE", to, "Order updates", "Acme asks for consent.")).block();

        catchers.get().uri("/dev/sms-catcher").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$[?(@.destination == '" + to + "')].body").isEqualTo("Acme asks for consent.");
    }

    @Test
    void anOtpGoesOutByEmailSmsAndWhatsApp() throws Exception {
        String email = "otp-" + System.nanoTime() + "@example.com";
        String phone = "+9197" + (System.nanoTime() % 100_000_000L);

        otp.handle(new OtpSendCmd(NtfChnl.EMAIL, email, "482913")).block();
        otp.handle(new OtpSendCmd(NtfChnl.SMS, phone, "551204")).block();
        otp.handle(new OtpSendCmd(NtfChnl.WHATSAPP, phone, "771930")).block();

        JsonNode mail = getJson("/api/v1/message/" + awaitMail(email).get("ID").asText());
        assertTrue(mail.get("Text").asText().contains("482913"));
        catchers.get().uri("/dev/sms-catcher").exchange().expectStatus().isOk()
                .expectBody(String.class).value(body -> assertTrue(body.contains("551204")));
        catchers.get().uri("/dev/whatsapp-catcher").exchange().expectStatus().isOk()
                .expectBody(String.class).value(body -> assertTrue(body.contains("771930")));
    }

    @Test
    void aLinkedPrincipalIsPushedOnEveryDeviceWithTheRequestId() {
        String dp = "dp-" + System.nanoTime();
        pushDevices.upsert(dp, "browser-token-" + dp, PushPltf.WEB).block();
        pushDevices.upsert(dp, "phone-token-" + dp, PushPltf.ANDROID).block();

        notifyDp.handle(new NotifyDpCmd("req-3", "df-a", "APP", dp, "Order updates", "the full text stays in the app"))
                .block();

        catchers.get().uri("/dev/push-catcher").exchange().expectStatus().isOk().expectBody()
                .jsonPath("$[?(@.destination == 'browser-token-" + dp + "')].data.cnsReqId").isEqualTo("req-3")
                .jsonPath("$[?(@.destination == 'phone-token-" + dp + "')].data.cnsReqId").isEqualTo("req-3")
                .jsonPath("$[?(@.destination == 'phone-token-" + dp + "')].body").isEqualTo("Order updates");
    }

    @Test
    void anEmailRefusedForGoodIsReportedBackToTheConsentLifecycle() {
        NtfDispatchService refusing = new NtfDispatchService(
                msg -> Mono.error(new NtfDeliveryRejectedException("mailbox-unavailable", "550 no such user")),
                msg -> Mono.empty(), msg -> Mono.empty(), msg -> Mono.empty());
        NotifyDpCmdHandler handler = new NotifyDpCmdHandler(refusing, pushDevices, (cnsReqId, channel, code) ->
                Mono.fromRunnable(() -> FAILED_DELIVERIES.add(cnsReqId + ":" + channel + ":" + code)));

        handler.handle(new NotifyDpCmd("req-4", "df-a", "EMAIL", "nobody@example.com", "t", "x")).block();

        assertTrue(FAILED_DELIVERIES.contains("req-4:EMAIL:mailbox-unavailable"), FAILED_DELIVERIES.toString());
    }

    /** Mailpit takes the message over SMTP a moment before its API lists it. */
    private static JsonNode awaitMail(String to) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            for (JsonNode message : getJson("/api/v1/search?query=" + java.net.URLEncoder.encode("to:" + to,
                    java.nio.charset.StandardCharsets.UTF_8)).get("messages")) {
                return message;
            }
            Thread.sleep(200);
        }
        fail("no email to " + to + " in Mailpit");
        return null;
    }

    private static JsonNode getJson(String path) throws Exception {
        HttpResponse<String> response = HTTP.send(HttpRequest.newBuilder(URI.create(
                "http://" + MAILPIT.getHost() + ":" + MAILPIT.getMappedPort(8025) + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        return JSON.readTree(response.body());
    }
}
