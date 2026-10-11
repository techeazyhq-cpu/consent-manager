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

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Registers cm-res-kit's 503 handler in every service that has the kit on its classpath, as cm-web-kit does. */
@AutoConfiguration
@ConditionalOnClass(WebHttpHandlerBuilder.class)
public class CmResKitAutoConfiguration {

    @Bean
    public DependencyUnavailableWebExceptionHandler dependencyUnavailableWebExceptionHandler(
            ObjectMapper objectMapper,
            @Value("${resilience4j.circuitbreaker.configs.default.wait-duration-in-open-state:30s}") Duration retryAfter) {
        return new DependencyUnavailableWebExceptionHandler(objectMapper, retryAfter);
    }
}
