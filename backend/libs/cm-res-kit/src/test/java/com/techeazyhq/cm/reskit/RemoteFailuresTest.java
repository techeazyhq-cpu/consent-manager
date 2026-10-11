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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.ConnectException;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import reactor.core.publisher.Mono;

class RemoteFailuresTest {

    @Test
    void anUnreachableSlowOrFailingRemoteCountsAgainstTheBreaker() {
        assertTrue(RemoteFailures.isRemoteFailure(new ConnectException("refused")));
        assertTrue(RemoteFailures.isRemoteFailure(new TimeoutException()));
        assertTrue(RemoteFailures.isRemoteFailure(new RemoteServerError("HTTP 503")));
        assertTrue(RemoteFailures.isRemoteFailure(response(HttpStatus.INTERNAL_SERVER_ERROR)));
        assertTrue(RemoteFailures.isRemoteFailure(response(HttpStatus.TOO_MANY_REQUESTS)));
        assertTrue(RemoteFailures.isRemoteFailure(new IllegalStateException("wrapped", new ConnectException())));
    }

    @Test
    void aRemoteThatAnswersAsDesignedDoesNot() {
        assertFalse(RemoteFailures.isRemoteFailure(response(HttpStatus.NOT_FOUND)));
        assertFalse(RemoteFailures.isRemoteFailure(response(HttpStatus.CONFLICT)));
        assertFalse(RemoteFailures.isRemoteFailure(new IllegalArgumentException("a bug of ours")));
    }

    @Test
    void anOpenBreakerOrAnUnhealthyRemoteBecomesDependencyUnavailable() {
        CallNotPermittedException open = CallNotPermittedException.createCallNotPermittedException(
                CircuitBreaker.ofDefaults("keycloak-admin"));

        Throwable fromOpen = DependencyFallbacks.unavailable("keycloak-admin").apply(open)
                .onErrorResume(Mono::just).cast(Throwable.class).block();
        Throwable fromRefused = DependencyFallbacks.unavailable("keycloak-admin").apply(new ConnectException())
                .onErrorResume(Mono::just).cast(Throwable.class).block();

        assertInstanceOf(DependencyUnavailableException.class, fromOpen);
        assertInstanceOf(DependencyUnavailableException.class, fromRefused);
    }

    @Test
    void anyOtherErrorReachesTheCallerUnchanged() {
        WebClientResponseException conflict = response(HttpStatus.CONFLICT);

        Throwable passed = DependencyFallbacks.unavailable("keycloak-admin").apply(conflict)
                .onErrorResume(Mono::just).cast(Throwable.class).block();

        assertSame(conflict, passed);
    }

    private static WebClientResponseException response(HttpStatus status) {
        return WebClientResponseException.create(status.value(), status.getReasonPhrase(), null, null, null);
    }
}
