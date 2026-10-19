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

package com.techeazyhq.cm.audkit;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import reactor.core.publisher.Mono;

/**
 * Where a service records what was done (ADR-040). Called inside the change's own transaction, so an outbox-backed
 * trail records the action exactly when the change commits. The actor comes from the request ({@link Actors}).
 */
@FunctionalInterface
public interface AuditTrail {

    /** Records nothing: for tests and for services that aren't audited. */
    AuditTrail NONE = (action, dfId, target, details) -> Mono.empty();

    Mono<Void> record(String action, String dfId, String target, Map<String, String> details);

    default Mono<Void> record(String action, String dfId, String target) {
        return record(action, dfId, target, Map.of());
    }

    /** A trail that hands each action, stamped with the current actor, to {@code sink}, such as an outbox. */
    static AuditTrail to(String ctx, Function<PlatformActionEvt, Mono<Void>> sink) {
        return (action, dfId, target, details) -> Actors.current()
                .flatMap(actor -> sink.apply(PlatformActionEvt.of(ctx, action, dfId, target, actor, details)));
    }

    /** Details in the order given, skipping null values: {@code details("from", "FREE", "to", "STANDARD")}. */
    static Map<String, String> details(String... keysAndValues) {
        if (keysAndValues.length % 2 != 0) {
            throw new IllegalArgumentException("details come in key, value pairs");
        }
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            if (keysAndValues[i + 1] != null) {
                map.put(keysAndValues[i], keysAndValues[i + 1]);
            }
        }
        return map;
    }
}
