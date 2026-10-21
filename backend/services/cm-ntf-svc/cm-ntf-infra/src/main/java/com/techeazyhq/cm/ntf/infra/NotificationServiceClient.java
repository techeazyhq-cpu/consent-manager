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
import com.techeazyhq.cm.ntf.dom.NtfChnl;
import com.techeazyhq.cm.ntf.dom.NtfCtg;
import com.techeazyhq.cm.ntf.dom.NtfDeliveryRejectedException;
import com.techeazyhq.cm.ntf.dom.NtfMsg;
import com.techeazyhq.cm.reskit.DependencyFallbacks;
import com.techeazyhq.cm.reskit.RemoteServerError;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

/**
 * Delivers every channel through TechEazyHQ's notification-service ({@code POST /v1/notifications}), which owns the
 * provider side: rate limits, retries and failover between SMTP/SMS/WhatsApp/push gateways. This client only has to
 * get the request accepted once:
 * <ul>
 *   <li>{@code Idempotency-Key} is the message's {@code ref}, so a retried call or a redelivered Pulsar command
 *       returns the original request instead of sending again;</li>
 *   <li>429, 5xx, timeouts and connection failures are retried with backoff (re-signed each attempt); any other
 *       refusal is final and surfaces as {@link NtfDeliveryRejectedException};</li>
 *   <li>OTPs go as {@code category: OTP}, so they are delivered ahead of other traffic and never after
 *       {@code otpValiditySeconds};</li>
 *   <li>a push's {@code data} (e.g. {@code cnsReqId}) goes as {@code data}, which notification-service hands to FCM
 *       next to its own {@code messageId}, unless {@code pushData} is off;</li>
 *   <li>a send, retries included, runs behind a circuit breaker (ADR-016): once notification-service keeps failing,
 *       sends fail at once with {@code DependencyUnavailableException} instead of each waiting out its retries, and
 *       the Pulsar command is redelivered later. A refusal does not count against the breaker.</li>
 * </ul>
 */
public class NotificationServiceClient {

    static final String SEND_PATH = "/v1/notifications";

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final URI sendUri;
    private final Settings settings;
    private final ReactiveCircuitBreaker circuitBreaker;

    public NotificationServiceClient(WebClient.Builder webClientBuilder, ObjectMapper objectMapper,
                                     ReactiveCircuitBreakerFactory<?, ?> cbFactory, Settings settings) {
        this.webClient = webClientBuilder.build();
        this.circuitBreaker = cbFactory.create("notification-service");
        this.objectMapper = objectMapper;
        this.sendUri = URI.create(settings.baseUrl().replaceAll("/+$", "") + SEND_PATH);
        this.settings = settings;
    }

    /**
     * @param signingSecret {@code nss_...}; null or blank sends unsigned requests
     * @param fromAddress   a sender address verified in notification-service; null or blank uses its default sender
     * @param pushData      whether a push's {@code data} is sent (notification-service accepts it from the version
     *                      that added push data; older ones ignore it)
     */
    public record Settings(String baseUrl, String apiKey, String signingSecret, String fromAddress,
                           int otpValiditySeconds, Duration timeout, int maxRetries, Duration retryBackoff,
                           boolean pushData) {

        public Settings(String baseUrl, String apiKey, String signingSecret, String fromAddress,
                        int otpValiditySeconds, Duration timeout, int maxRetries, Duration retryBackoff) {
            this(baseUrl, apiKey, signingSecret, fromAddress, otpValiditySeconds, timeout, maxRetries, retryBackoff, true);
        }
    }

    public Mono<Void> send(NtfChnl channel, NtfMsg msg) {
        if (channel == NtfChnl.APP) {
            return Mono.error(new IllegalArgumentException("APP notifications are the DP dashboard, not a delivery"));
        }
        byte[] body = toJson(channel, msg);
        String idempotencyKey = msg.ref() != null ? msg.ref() : UUID.randomUUID().toString();
        Mono<Void> retried = Mono.defer(() -> post(body, idempotencyKey))
                .retryWhen(Retry.backoff(settings.maxRetries(), settings.retryBackoff()).filter(this::isTransient))
                .onErrorMap(Exceptions::isRetryExhausted, Throwable::getCause);
        return circuitBreaker.run(retried, DependencyFallbacks.unavailable("notification-service"));
    }

    private Mono<Void> post(byte[] body, String idempotencyKey) {
        return webClient.post()
                .uri(sendUri)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> {
                    headers.set("X-API-Key", settings.apiKey());
                    headers.set("Idempotency-Key", idempotencyKey);
                    if (settings.signingSecret() != null && !settings.signingSecret().isBlank()) {
                        long timestamp = Instant.now().getEpochSecond();
                        String nonce = UUID.randomUUID().toString().replace("-", "");
                        headers.set("X-Signature-Timestamp", Long.toString(timestamp));
                        headers.set("X-Signature-Nonce", nonce);
                        headers.set("X-Signature", NtfRequestSigner.sign(settings.signingSecret(), timestamp, nonce,
                                "POST", sendUri.getRawPath(), sendUri.getRawQuery(), body));
                    }
                })
                .bodyValue(body)
                .exchangeToMono(response -> response.statusCode().is2xxSuccessful()
                        ? response.releaseBody()
                        : response.bodyToMono(String.class).defaultIfEmpty("")
                                .flatMap(error -> Mono.<Void>error(toFailure(response.statusCode(), error))))
                .timeout(settings.timeout());
    }

    private byte[] toJson(NtfChnl channel, NtfMsg msg) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("channel", channel.name());
        request.put("recipient", msg.destination());
        if (msg.subject() != null) {
            request.put("subject", msg.subject());
        }
        request.put("body", msg.body());
        if (msg.ref() != null) {
            request.put("clientReference", msg.ref());
        }
        request.put("category", msg.category().name());
        if (msg.category() == NtfCtg.OTP) {
            request.put("validitySeconds", settings.otpValiditySeconds());
        }
        if (channel == NtfChnl.EMAIL && settings.fromAddress() != null && !settings.fromAddress().isBlank()) {
            request.put("from", settings.fromAddress());
        }
        if (channel == NtfChnl.PUSH && settings.pushData() && !msg.data().isEmpty()) {
            request.put("data", msg.data());
        }
        try {
            return objectMapper.writeValueAsBytes(request);
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialise notification request", e);
        }
    }

    private RuntimeException toFailure(HttpStatusCode status, String errorBody) {
        String code = "HTTP_" + status.value();
        String message = errorBody;
        boolean retryable = status.value() == 429 || status.is5xxServerError();
        try {
            JsonNode error = objectMapper.readTree(errorBody);
            code = error.path("code").asText(code);
            message = error.path("message").asText(message);
            retryable = retryable || error.path("retryable").asBoolean(false);
        } catch (Exception ignored) {
            // not the service's error shape (e.g. a proxy's HTML page): keep the HTTP status as the code
        }
        String description = "notification-service refused the request: " + code + " (HTTP " + status.value() + ") " + message;
        return retryable ? new TransientFailure(description) : new NtfDeliveryRejectedException(code, description);
    }

    private boolean isTransient(Throwable error) {
        return error instanceof TransientFailure
                || error instanceof WebClientRequestException
                || error instanceof TimeoutException;
    }

    /** A failure the same request may get past later; left to the caller (e.g. a Pulsar redelivery) once retries run out. */
    static final class TransientFailure extends RemoteServerError {
        TransientFailure(String message) {
            super(message);
        }
    }
}
