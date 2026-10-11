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

import java.util.function.Function;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Circuit breaker fallbacks shared by every service's outbound adapters (ADR-016). */
public final class DependencyFallbacks {

    private DependencyFallbacks() {
    }

    /**
     * For a call that has no safe default answer: an open breaker or an unhealthy remote becomes
     * {@link DependencyUnavailableException} (503); any other error, such as a 4xx refusal, reaches the caller as it
     * was raised.
     */
    public static <T> Function<Throwable, Mono<T>> unavailable(String dependency) {
        return error -> Mono.error(translate(dependency, error));
    }

    /** {@link #unavailable(String)} for a call that answers with many items. */
    public static <T> Function<Throwable, Flux<T>> unavailableMany(String dependency) {
        return error -> Flux.error(translate(dependency, error));
    }

    private static Throwable translate(String dependency, Throwable error) {
        return error instanceof CallNotPermittedException || RemoteFailures.isRemoteFailure(error)
                ? new DependencyUnavailableException(dependency, error)
                : error;
    }
}
