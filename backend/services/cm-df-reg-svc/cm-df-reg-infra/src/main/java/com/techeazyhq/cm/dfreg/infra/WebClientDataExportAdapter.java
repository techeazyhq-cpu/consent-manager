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

import com.techeazyhq.cm.dfreg.dom.DataExportPort;

import reactor.core.publisher.Mono;

/** {@link DataExportPort} against cm-cns-cmd-svc's and cm-aud-svc's {@code GET /internal/dfs/{id}/export}. */
public class WebClientDataExportAdapter implements DataExportPort {

    private static final int LIMIT = 5_000;

    private final WebClient cnsCmd;
    private final WebClient aud;

    public WebClientDataExportAdapter(WebClient.Builder builder, String cnsCmdBaseUrl, String audBaseUrl) {
        this.cnsCmd = builder.clone().baseUrl(cnsCmdBaseUrl).codecs(c -> c.defaultCodecs().maxInMemorySize(256 * 1024 * 1024)).build();
        this.aud = builder.clone().baseUrl(audBaseUrl).codecs(c -> c.defaultCodecs().maxInMemorySize(256 * 1024 * 1024)).build();
    }

    @Override
    public Mono<Map<String, Object>> consentsAndPurposes(String dfId) {
        return get(cnsCmd, dfId);
    }

    @Override
    public Mono<Map<String, Object>> auditEntries(String dfId) {
        return get(aud, dfId);
    }

    @SuppressWarnings("unchecked")
    private static Mono<Map<String, Object>> get(WebClient service, String dfId) {
        return service.get().uri("/internal/dfs/{id}/export?limit={n}", dfId, LIMIT)
                .retrieve().bodyToMono(Map.class).map(m -> (Map<String, Object>) m)
                .timeout(Duration.ofSeconds(180));
    }
}
