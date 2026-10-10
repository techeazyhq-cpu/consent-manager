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

package com.techeazyhq.cm.webkit;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Registers cm-web-kit's WebFlux cross-cutting beans without relying on component-scan
 * reaching {@code com.techeazyhq.cm.webkit} from each service's own base package — standard
 * Spring Boot "starter" auto-configuration mechanism (META-INF/spring/...AutoConfiguration.imports).
 */
@AutoConfiguration
@ConditionalOnClass(WebHttpHandlerBuilder.class)
public class CmWebKitAutoConfiguration {

    @Bean
    public WebFilter correlationIdWebFilter() {
        return new CorrelationIdWebFilter();
    }

    @Bean
    public DomProblemDetailWebExceptionHandler domProblemDetailWebExceptionHandler(ObjectMapper objectMapper) {
        return new DomProblemDetailWebExceptionHandler(objectMapper);
    }
}
