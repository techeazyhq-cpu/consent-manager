package com.techeazyhq.cm.idn.otp;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** {@link NtfOtpSender} against a stand-in for cm-ntf-svc's {@code /internal/otp/send}. */
class NtfOtpSenderTest {

    private final NtfOtpSender sender = new NtfOtpSender(HttpClient.newHttpClient(), Duration.ofSeconds(5));
    private final List<String> bodies = new CopyOnWriteArrayList<>();
    private final AtomicInteger status = new AtomicInteger(200);
    private HttpServer ntf;

    @BeforeEach
    void start() throws IOException {
        ntf = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        ntf.createContext("/internal/otp/send", exchange -> {
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(status.get(), -1);
            exchange.close();
        });
        ntf.start();
    }

    @AfterEach
    void stop() {
        ntf.stop(0);
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + ntf.getAddress().getPort() + "/";
    }

    @Test
    void postsTheChannelDestinationAndCode() {
        OtpIdentifier phone = OtpIdentifier.parse("+919876543210").orElseThrow();

        assertEquals(NtfOtpSender.Outcome.SENT, sender.send(baseUrl(), phone, "042137"));
        assertEquals("{\"channel\":\"SMS\",\"destination\":\"+919876543210\",\"code\":\"042137\"}", bodies.get(0));
    }

    @Test
    void aRefusedDestinationIsRejectedAndAnythingElseFailed() {
        OtpIdentifier email = OtpIdentifier.parse("ann@example.com").orElseThrow();

        status.set(422);
        assertEquals(NtfOtpSender.Outcome.REJECTED, sender.send(baseUrl(), email, "123456"));
        status.set(503);
        assertEquals(NtfOtpSender.Outcome.FAILED, sender.send(baseUrl(), email, "123456"));
        assertEquals(NtfOtpSender.Outcome.FAILED, sender.send("http://127.0.0.1:1", email, "123456"));
        assertEquals(NtfOtpSender.Outcome.FAILED, sender.send("not a url", email, "123456"));
    }
}
