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

package com.techeazyhq.cm.idn.otp;

import org.jboss.logging.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Hands a generated code to cm-ntf-svc's {@code POST /internal/otp/send} (plan §9a), which delivers it — through
 * notification-service when configured, as an OTP-category message that jumps the queue and is never sent late.
 * cm-ntf-svc never generates or verifies codes; this side never delivers them.
 */
public class NtfOtpSender {

    private static final Logger LOG = Logger.getLogger(NtfOtpSender.class);

    public enum Outcome {
        SENT,
        /** cm-ntf-svc refused the destination for good (e.g. a number the SMS gateway rejects). */
        REJECTED,
        FAILED
    }

    private final HttpClient http;
    private final Duration timeout;

    public NtfOtpSender(HttpClient http, Duration timeout) {
        this.http = http;
        this.timeout = timeout;
    }

    public Outcome send(String ntfBaseUrl, OtpIdentifier to, String code) {
        String body = "{\"channel\":\"" + to.channel() + "\",\"destination\":\"" + to.value() + "\",\"code\":\"" + code + "\"}";
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(ntfBaseUrl.replaceAll("/+$", "") + "/internal/otp/send"))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            int status = http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            if (status >= 200 && status < 300) {
                return Outcome.SENT;
            }
            LOG.warnf("cm-ntf-svc answered HTTP %d to a %s sign-in code", status, to.channel());
            return status == 422 ? Outcome.REJECTED : Outcome.FAILED;
        } catch (IOException | IllegalArgumentException e) {
            LOG.warnf("Could not reach cm-ntf-svc to send a %s sign-in code: %s", to.channel(), e.getClass().getSimpleName());
            return Outcome.FAILED;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Outcome.FAILED;
        }
    }
}
