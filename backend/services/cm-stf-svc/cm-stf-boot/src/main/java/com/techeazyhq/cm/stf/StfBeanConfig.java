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

package com.techeazyhq.cm.stf;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import com.techeazyhq.cm.kckit.KeycloakAdminAuthFilter;
import com.techeazyhq.cm.stf.app.CreateOperatorCmdHandler;
import com.techeazyhq.cm.stf.app.ListStfQryHandler;
import com.techeazyhq.cm.stf.app.MyAcctHandler;
import com.techeazyhq.cm.stf.app.SetStfEnabledCmdHandler;
import com.techeazyhq.cm.stf.dom.StfDirectoryPort;
import com.techeazyhq.cm.stf.infra.KeycloakStfDirectoryAdapter;

/** Composition root (ADR-002) for the platform staff service. */
@Configuration
@EnableConfigurationProperties(StfBeanConfig.KeycloakProps.class)
public class StfBeanConfig {

    @Bean
    public StfDirectoryPort stfDirectoryPort(WebClient.Builder builder, ReactiveCircuitBreakerFactory<?, ?> cbFactory,
                                             KeycloakProps props) {
        KeycloakAdminAuthFilter authFilter = new KeycloakAdminAuthFilter(
                builder.clone(), props.baseUrl(), props.adminUsername(), props.adminPassword());
        WebClient admin = builder.clone().baseUrl(props.baseUrl()).filter(authFilter.filter()).build();
        return new KeycloakStfDirectoryAdapter(admin, cbFactory, props.realm());
    }

    @Bean
    public CreateOperatorCmdHandler createOperatorCmdHandler(StfDirectoryPort directory) {
        return new CreateOperatorCmdHandler(directory);
    }

    @Bean
    public SetStfEnabledCmdHandler setStfEnabledCmdHandler(StfDirectoryPort directory) {
        return new SetStfEnabledCmdHandler(directory);
    }

    @Bean
    public ListStfQryHandler listStfQryHandler(StfDirectoryPort directory) {
        return new ListStfQryHandler(directory);
    }

    @Bean
    public MyAcctHandler myAcctHandler(StfDirectoryPort directory) {
        return new MyAcctHandler(directory);
    }

    @ConfigurationProperties(prefix = "cm.keycloak")
    public record KeycloakProps(String baseUrl, String realm, String adminUsername, String adminPassword) {
    }
}
