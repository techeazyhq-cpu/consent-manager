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
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.web.reactive.function.client.WebClient;

import com.techeazyhq.cm.dfreg.dom.RecordsPurgePort;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * {@link RecordsPurgePort} against each service's {@code POST /internal/dfs/{id}/purge}, asked again until a call deletes
 * nothing. A failure is passed on: the pass is tried again later, and every call is idempotent.
 */
public class WebClientRecordsPurgeAdapter implements RecordsPurgePort {

    private static final int BATCH = 500;
    private static final int MAX_BATCHES = 10_000;

    private final List<WebClient> services;

    public WebClientRecordsPurgeAdapter(WebClient.Builder builder, List<String> baseUrls) {
        this.services = baseUrls.stream().map(url -> builder.clone().baseUrl(url).build()).toList();
    }

    @Override
    public Mono<Result> purge(String dfId, Instant before) {
        return Flux.fromIterable(services).concatMap(service -> drain(service, dfId, before))
                .reduce(new Result(0, 0), (sum, r) -> new Result(sum.deleted() + r.deleted(), sum.remaining() + r.remaining()));
    }

    private Mono<Result> drain(WebClient service, String dfId, Instant before) {
        return call(service, dfId, before)
                .expand(last -> last.deleted() == 0 ? Mono.empty() : call(service, dfId, before))
                .take(MAX_BATCHES)
                .reduce(new Result(0, 0), (sum, r) -> new Result(sum.deleted() + r.deleted(), r.remaining()));
    }

    @SuppressWarnings("unchecked")
    private Mono<Result> call(WebClient service, String dfId, Instant before) {
        return service.post().uri("/internal/dfs/{id}/purge?before={before}&limit={n}", dfId, before.toString(), BATCH)
                .retrieve().bodyToMono(Map.class).map(m -> (Map<String, Object>) m)
                .map(m -> new Result(n(m, "deleted"), n(m, "remaining")))
                .timeout(Duration.ofSeconds(120));
    }

    private static long n(Map<String, Object> m, String key) {
        return m.get(key) instanceof Number number ? number.longValue() : 0;
    }
}
