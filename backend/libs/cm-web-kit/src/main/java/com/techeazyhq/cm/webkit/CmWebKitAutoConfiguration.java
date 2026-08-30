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
