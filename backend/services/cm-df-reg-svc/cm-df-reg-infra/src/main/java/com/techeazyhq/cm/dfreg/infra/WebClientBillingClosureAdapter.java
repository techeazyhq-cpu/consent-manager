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

import org.springframework.web.reactive.function.client.WebClient;

import com.techeazyhq.cm.dfreg.dom.BillingClosurePort;

import reactor.core.publisher.Mono;

/**
 * {@link BillingClosurePort} against cm-bil-svc's {@code PUT /internal/dfs/{id}/billing-end}. A failure is passed on: the closing
 * is tried again on the next pass, and ending billing twice changes nothing.
 */
public class WebClientBillingClosureAdapter implements BillingClosurePort {

    private final WebClient webClient;

    public WebClientBillingClosureAdapter(WebClient.Builder builder, String bilBaseUrl) {
        this.webClient = builder.baseUrl(bilBaseUrl).build();
    }

    @Override
    public Mono<Void> endBilling(String dfId) {
        return webClient.put().uri("/internal/dfs/{id}/billing-end", dfId)
                .retrieve().toBodilessEntity().timeout(Duration.ofSeconds(15)).then();
    }
}
