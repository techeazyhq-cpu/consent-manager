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

package com.techeazyhq.cm.dfreg;

import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.reactive.client.adapter.AdaptedReactivePulsarClientFactory;
import org.apache.pulsar.reactive.client.api.ReactivePulsarClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeazyhq.cm.dfreg.app.ActivateDfCmdHandler;
import com.techeazyhq.cm.dfreg.app.DfTeamHandler;
import com.techeazyhq.cm.dfreg.app.GetDfActiveQryHandler;
import com.techeazyhq.cm.dfreg.app.GetDfLogoQryHandler;
import com.techeazyhq.cm.dfreg.app.GetDfQryHandler;
import com.techeazyhq.cm.dfreg.app.ListDfsQryHandler;
import com.techeazyhq.cm.dfreg.app.RegisterDfCmdHandler;
import com.techeazyhq.cm.dfreg.app.UploadDfLogoCmdHandler;
import com.techeazyhq.cm.dfreg.dom.DfApiClientProvisionPort;
import com.techeazyhq.cm.dfreg.dom.DfLogoRepoPort;
import com.techeazyhq.cm.dfreg.dom.DfMemberDirectoryPort;
import com.techeazyhq.cm.dfreg.dom.DfRepoPort;
import com.techeazyhq.cm.dfreg.dom.ObxPort;
import com.techeazyhq.cm.dfreg.dom.SubPlnCatalogPort;
import com.techeazyhq.cm.kckit.KeycloakAdminAuthFilter;
import com.techeazyhq.cm.dfreg.infra.KeycloakDfApiClientProvisionAdapter;
import com.techeazyhq.cm.dfreg.infra.KeycloakDfMemberDirectoryAdapter;
import com.techeazyhq.cm.audkit.AuditTrail;
import com.techeazyhq.cm.dfreg.infra.ObxRelay;
import com.techeazyhq.cm.dfreg.infra.PulsarDfEvtProducer;
import com.techeazyhq.cm.dfreg.infra.R2dbcDfLogoAdapter;
import com.techeazyhq.cm.dfreg.infra.R2dbcDfRepoAdapter;
import com.techeazyhq.cm.dfreg.infra.R2dbcObxAdapter;
import com.techeazyhq.cm.dfreg.infra.WebClientSubPlnCatalogAdapter;

@Configuration
@EnableConfigurationProperties(DfRegBeanConfig.KeycloakProps.class)
public class DfRegBeanConfig {

    private static final Logger LOG = LoggerFactory.getLogger(DfRegBeanConfig.class);

    @Bean
    public DfRepoPort dfRepoPort(DatabaseClient db, ObjectMapper objectMapper) {
        return new R2dbcDfRepoAdapter(db, objectMapper);
    }

    @Bean
    public DfLogoRepoPort dfLogoRepoPort(DatabaseClient db) {
        return new R2dbcDfLogoAdapter(db);
    }

    @Bean
    public SubPlnCatalogPort subPlnCatalogPort(WebClient.Builder builder, ReactiveCircuitBreakerFactory<?, ?> cbFactory,
                                               @Value("${cm.service-urls.bil}") String bilBaseUrl) {
        return new WebClientSubPlnCatalogAdapter(builder, cbFactory, bilBaseUrl);
    }

    @Bean
    public ObxPort obxPort(DatabaseClient db, ObjectMapper objectMapper) {
        return new R2dbcObxAdapter(db, objectMapper);
    }

    /** What people do to tenants and their teams, through the outbox to the audit ledger (ADR-040). */
    @Bean
    public AuditTrail auditTrail(ObxPort obxPort) {
        return AuditTrail.to("dfreg", evt -> obxPort.enqueue(evt, "PlatformAction", evt.target() != null ? evt.target() : "dfreg"));
    }

    @Bean
    public ObxRelay obxRelay(DatabaseClient db, PulsarDfEvtProducer producer) {
        return new ObxRelay(db, producer);
    }

    @Bean
    public WebClient keycloakAdminWebClient(KeycloakProps props) {
        KeycloakAdminAuthFilter authFilter = new KeycloakAdminAuthFilter(
                WebClient.builder(), props.baseUrl(), props.adminUsername(), props.adminPassword());
        return WebClient.builder().baseUrl(props.baseUrl()).filter(authFilter.filter()).build();
    }

    @Bean
    public DfApiClientProvisionPort dfApiClientProvisionPort(WebClient keycloakAdminWebClient,
                                                             ReactiveCircuitBreakerFactory<?, ?> cbFactory,
                                                             KeycloakProps props) {
        return new KeycloakDfApiClientProvisionAdapter(keycloakAdminWebClient, cbFactory, props.realm());
    }

    @Bean
    public KeycloakDfMemberDirectoryAdapter dfMemberDirectory(WebClient keycloakAdminWebClient,
                                                              ReactiveCircuitBreakerFactory<?, ?> cbFactory,
                                                              KeycloakProps props) {
        return new KeycloakDfMemberDirectoryAdapter(keycloakAdminWebClient, cbFactory, props.realm());
    }

    /** Sets up the realm for fiduciary members at start, so their first sign-in finds it ready; retried on first use. */
    @Bean
    public ApplicationRunner dfMemberRealmSetup(KeycloakDfMemberDirectoryAdapter dfMemberDirectory) {
        return args -> dfMemberDirectory.ensureRealmSetup()
                .doOnError(e -> LOG.warn("Keycloak isn't ready for fiduciary members yet, retrying on first use: {}",
                        e.getMessage()))
                .onErrorComplete()
                .subscribe();
    }

    @Bean
    public DfTeamHandler dfTeamHandler(DfMemberDirectoryPort dfMemberDirectory, DfRepoPort dfRepo, AuditTrail audit) {
        return new DfTeamHandler(dfMemberDirectory, dfRepo, audit);
    }

    @Bean(destroyMethod = "close")
    public PulsarClient pulsarClient(@Value("${cm.pulsar.service-url:pulsar://localhost:6650}") String serviceUrl)
            throws org.apache.pulsar.client.api.PulsarClientException {
        return PulsarClient.builder().serviceUrl(serviceUrl).build();
    }

    @Bean
    public ReactivePulsarClient reactivePulsarClient(PulsarClient pulsarClient) {
        return AdaptedReactivePulsarClientFactory.create(pulsarClient);
    }

    @Bean
    public PulsarDfEvtProducer pulsarDfEvtProducer(ReactivePulsarClient reactivePulsarClient) {
        return new PulsarDfEvtProducer(reactivePulsarClient);
    }

    @Bean
    public RegisterDfCmdHandler registerDfCmdHandler(DfRepoPort dfRepo, SubPlnCatalogPort subPlnCatalog, AuditTrail audit) {
        return new RegisterDfCmdHandler(dfRepo, subPlnCatalog, audit);
    }

    @Bean
    public UploadDfLogoCmdHandler uploadDfLogoCmdHandler(DfRepoPort dfRepo, DfLogoRepoPort logoRepo, AuditTrail audit) {
        return new UploadDfLogoCmdHandler(dfRepo, logoRepo, audit);
    }

    @Bean
    public GetDfLogoQryHandler getDfLogoQryHandler(DfLogoRepoPort logoRepo) {
        return new GetDfLogoQryHandler(logoRepo);
    }

    @Bean
    public ActivateDfCmdHandler activateDfCmdHandler(DfRepoPort dfRepo, DfApiClientProvisionPort provisionPort,
                                                     DfMemberDirectoryPort dfMemberDirectory, AuditTrail audit) {
        return new ActivateDfCmdHandler(dfRepo, provisionPort, dfMemberDirectory, audit);
    }

    @Bean
    public GetDfActiveQryHandler getDfActiveQryHandler(DfRepoPort dfRepo) {
        return new GetDfActiveQryHandler(dfRepo);
    }

    @Bean
    public ListDfsQryHandler listDfsQryHandler(DfRepoPort dfRepo) {
        return new ListDfsQryHandler(dfRepo);
    }

    @Bean
    public GetDfQryHandler getDfQryHandler(DfRepoPort dfRepo) {
        return new GetDfQryHandler(dfRepo);
    }

    @ConfigurationProperties(prefix = "cm.keycloak")
    public record KeycloakProps(String baseUrl, String realm, String adminUsername, String adminPassword) {
    }
}
