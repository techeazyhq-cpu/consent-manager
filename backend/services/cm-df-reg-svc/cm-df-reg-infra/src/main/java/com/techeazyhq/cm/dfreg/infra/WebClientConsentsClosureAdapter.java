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


package com.techeazyhq.cm.dfreg.infra;

import java.time.Duration;
import java.util.Map;

import org.springframework.web.reactive.function.client.WebClient;

import com.techeazyhq.cm.dfreg.dom.ConsentsClosurePort;

import reactor.core.publisher.Mono;

/**
 * {@link ConsentsClosurePort} against cm-cns-cmd-svc's {@code POST /internal/dfs/{id}/close-consents}, asked again until it
 * says it is done. A failure is passed on: the closing is tried again on the next pass, and each batch is idempotent.
 */
public class WebClientConsentsClosureAdapter implements ConsentsClosurePort {

    private static final int BATCH = 200;
    private static final int MAX_BATCHES = 5000;

    private final WebClient webClient;

    public WebClientConsentsClosureAdapter(WebClient.Builder builder, String cnsCmdBaseUrl) {
        this.webClient = builder.baseUrl(cnsCmdBaseUrl).build();
    }

    @Override
    public Mono<Result> closeConsents(String dfId) {
        return batch(dfId).expand(last -> Boolean.TRUE.equals(last.get("done")) ? Mono.empty() : batch(dfId))
                .take(MAX_BATCHES)
                .reduce(new Result(0, 0, 0), (sum, b) -> new Result(sum.revoked() + n(b, "revoked"),
                        sum.declined() + n(b, "declined"), sum.contactsErased() + n(b, "contactsErased")));
    }

    @SuppressWarnings("unchecked")
    private Mono<Map<String, Object>> batch(String dfId) {
        return webClient.post().uri("/internal/dfs/{id}/close-consents?limit={n}", dfId, BATCH)
                .retrieve().bodyToMono(Map.class).map(m -> (Map<String, Object>) m)
                .timeout(Duration.ofSeconds(120));
    }

    private static long n(Map<String, Object> m, String key) {
        return m.get(key) instanceof Number number ? number.longValue() : 0;
    }
}
