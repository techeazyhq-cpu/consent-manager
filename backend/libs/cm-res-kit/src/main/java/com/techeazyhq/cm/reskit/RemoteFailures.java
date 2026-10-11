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

package com.techeazyhq.cm.reskit;

import java.io.IOException;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Which failures say the remote service is unhealthy, and so count against its circuit breaker (ADR-016): it could
 * not be reached, it did not answer in time, it answered 5xx, or it is shedding load (429). Any other 4xx is the remote
 * working as designed (an unknown id, a refused request), so a burst of bad requests can never open the breaker on a
 * healthy service.
 * <p>
 * Every service names this class as the breakers' {@code record-failure-predicate}, so it needs a public no-argument
 * constructor.
 */
public class RemoteFailures implements Predicate<Throwable> {

    /**
     * Not every service calls out with WebClient (cm-aud-infra uses the JDK's HttpClient). Touching a WebClient class
     * without it on the classpath raises NoClassDefFoundError, which Reactor treats as fatal and drops, leaving the
     * call hanging, so those checks live in {@link WebClientFailures} and only run when WebClient is present.
     */
    private static final boolean WEB_CLIENT_PRESENT = isPresent(
            "org.springframework.web.reactive.function.client.WebClientResponseException");

    @Override
    public boolean test(Throwable error) {
        return isRemoteFailure(error);
    }

    public static boolean isRemoteFailure(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof IOException || cause instanceof TimeoutException || cause instanceof RemoteServerError) {
                return true;
            }
            if (WEB_CLIENT_PRESENT && WebClientFailures.isWebClientFailure(cause)) {
                return WebClientFailures.isRemoteFailure(cause);
            }
        }
        return false;
    }

    private static boolean isPresent(String className) {
        try {
            Class.forName(className, false, RemoteFailures.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    private static final class WebClientFailures {

        static boolean isWebClientFailure(Throwable error) {
            return error instanceof WebClientRequestException || error instanceof WebClientResponseException;
        }

        static boolean isRemoteFailure(Throwable error) {
            if (error instanceof WebClientResponseException response) {
                return response.getStatusCode().is5xxServerError() || response.getStatusCode().value() == 429;
            }
            return true; // WebClientRequestException: the request never got an answer
        }
    }
}
