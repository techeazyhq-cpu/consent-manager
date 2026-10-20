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

package com.techeazyhq.cm.cnscmd.dom;

import java.util.Map;

import reactor.core.publisher.Mono;

/**
 * Posts a signed webhook to an endpoint (ADR-047). Never follows redirects, and checks the address it connects to
 * isn't the platform's own network (SSRF), every time: a URL's DNS can change after it was registered.
 */
public interface WhkSenderPort {

    /** The HTTP status, or a null status and why there was no response. */
    record Result(Integer status, String error) {
    }

    Mono<Result> post(String url, Map<String, String> headers, String body);

    /** Why {@code url} may not be an endpoint (its host is the platform's own or private network), or empty. */
    Mono<java.util.Optional<String>> refusal(String url);
}
