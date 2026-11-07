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
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
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
import com.techeazyhq.cm.dfreg.app.DfApiKeyHandler;
import com.techeazyhq.cm.dfreg.app.DfClosureHandler;
import com.techeazyhq.cm.dfreg.app.DfLeavingHandler;
import com.techeazyhq.cm.dfreg.app.DfRetentionHandler;
import com.techeazyhq.cm.dfreg.app.DfSuspensionHandler;
import com.techeazyhq.cm.dfreg.app.DfTermsHandler;
import com.techeazyhq.cm.dfreg.dom.TermsDoc;
import com.techeazyhq.cm.dfreg.infra.R2dbcDfTermsAdapter;
import com.techeazyhq.cm.dfreg.app.VerifyApiRequestHandler;
import com.techeazyhq.cm.dfreg.dom.DfAccessTokenPort;
import com.techeazyhq.cm.dfreg.dom.SecretSealerPort;
import com.techeazyhq.cm.dfreg.infra.AesGcmSecretSealer;
import com.techeazyhq.cm.dfreg.infra.KeycloakDfAccessTokenAdapter;
import com.techeazyhq.cm.dfreg.infra.NoncePurgeJob;
import com.techeazyhq.cm.dfreg.infra.R2dbcDfApiKeyAdapter;
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
    public R2dbcDfRepoAdapter dfRepoPort(DatabaseClient db, ObjectMapper objectMapper, SecretSealerPort secretSealer) {
        return new R2dbcDfRepoAdapter(db, objectMapper, secretSealer);
    }

    /** Seals the API client secrets stored before they were sealed (#220); reads accept either until it has run. */
    @Bean
    public ApplicationRunner dfClientSecretSealing(R2dbcDfRepoAdapter dfRepo) {
        return args -> dfRepo.sealPlainTextSecrets()
                .doOnNext(sealed -> {
                    if (sealed > 0) {
                        LOG.info("Sealed the API client secrets of {} fiduciaries", sealed);
                    }
                })
                .doOnError(e -> LOG.warn("Couldn't seal stored API client secrets, retrying at the next start: {}",
                        e.getMessage()))
                .onErrorComplete()
                .subscribe();
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

    /** Usage alerts (ADR-060): cm-bil-svc says a fiduciary is near its allowance; this service holds who to tell. */
    @Bean
    public com.techeazyhq.cm.dfreg.dom.EmailPort emailPort(PulsarDfEvtProducer producer, ObjectMapper objectMapper) {
        return new com.techeazyhq.cm.dfreg.infra.PulsarEmailAdapter(producer, objectMapper);
    }

    @Bean
    public com.techeazyhq.cm.dfreg.app.OnUsgThresholdEvtHandler onUsgThresholdEvtHandler(DfRepoPort dfs,
            com.techeazyhq.cm.dfreg.dom.EmailPort email,
            @Value("${cm.dfreg.usage-alerts.platform-emails:}") java.util.List<String> platformEmails) {
        return new com.techeazyhq.cm.dfreg.app.OnUsgThresholdEvtHandler(dfs, email,
                platformEmails.stream().map(String::trim).filter(s -> !s.isEmpty()).toList());
    }

    @Bean
    public com.techeazyhq.cm.dfreg.infra.PulsarUsgThresholdConsumer pulsarUsgThresholdConsumer(
            ReactivePulsarClient reactivePulsarClient, ObjectMapper objectMapper,
            com.techeazyhq.cm.dfreg.app.OnUsgThresholdEvtHandler handler) {
        return new com.techeazyhq.cm.dfreg.infra.PulsarUsgThresholdConsumer(reactivePulsarClient, objectMapper, handler);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startConsumers(ApplicationReadyEvent event) {
        event.getApplicationContext().getBean(com.techeazyhq.cm.dfreg.infra.PulsarUsgThresholdConsumer.class).start();
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

    // ---- API keys and signed requests (ADR-041) ----

    @Bean
    public R2dbcDfApiKeyAdapter dfApiKeyAdapter(DatabaseClient db) {
        return new R2dbcDfApiKeyAdapter(db);
    }

    @Bean
    public SecretSealerPort secretSealer(@Value("${cm.dfreg.secrets-key}") String secretsKey) {
        return new AesGcmSecretSealer(secretsKey);
    }

    @Bean
    public DfAccessTokenPort dfAccessTokenPort(WebClient.Builder builder, KeycloakProps props,
                                               @Value("${cm.keycloak.token-host:}") String tokenHost) {
        return new KeycloakDfAccessTokenAdapter(builder, props.baseUrl(), props.realm(), tokenHost);
    }

    @Bean
    public DfApiKeyHandler dfApiKeyHandler(R2dbcDfApiKeyAdapter keys, SecretSealerPort sealer, AuditTrail audit,
                                           DfRepoPort dfRepo) {
        return new DfApiKeyHandler(keys, sealer, audit, dfRepo);
    }

    @Bean
    public VerifyApiRequestHandler verifyApiRequestHandler(R2dbcDfApiKeyAdapter keys, DfRepoPort dfRepo,
                                                           SecretSealerPort sealer, DfAccessTokenPort tokens) {
        return new VerifyApiRequestHandler(keys, dfRepo, keys, sealer, tokens, java.time.Clock.systemUTC());
    }

    @Bean
    public com.techeazyhq.cm.dfreg.dom.ConsentsClosurePort consentsClosurePort(WebClient.Builder builder,
            @Value("${cm.service-urls.cns-cmd}") String cnsCmdBaseUrl) {
        return new com.techeazyhq.cm.dfreg.infra.WebClientConsentsClosureAdapter(builder, cnsCmdBaseUrl);
    }

    @Bean
    public DfClosureHandler dfClosureHandler(DfRepoPort dfRepo, com.techeazyhq.cm.dfreg.dom.ConsentsClosurePort consents,
                                             R2dbcDfApiKeyAdapter keys, DfMemberDirectoryPort members,
                                             com.techeazyhq.cm.dfreg.dom.DfLogoRepoPort logos, AuditTrail audit) {
        return new DfClosureHandler(dfRepo, consents, keys, members, logos, audit, java.time.Clock.systemUTC());
    }

    @Bean
    public DfClosingJob dfClosingJob(DfClosureHandler closure) {
        return new DfClosingJob(closure);
    }

    @Bean
    public com.techeazyhq.cm.dfreg.dom.RecordsPurgePort recordsPurgePort(WebClient.Builder builder,
            @Value("${cm.service-urls.cns-cmd}") String cnsCmd, @Value("${cm.service-urls.cns-qry}") String cnsQry,
            @Value("${cm.service-urls.aud}") String aud) {
        return new com.techeazyhq.cm.dfreg.infra.WebClientRecordsPurgeAdapter(builder, java.util.List.of(cnsCmd, cnsQry, aud));
    }

    /** How long a closed fiduciary's records are kept (ADR-064): 7 years by default; to be confirmed with counsel per cell. */
    @Bean
    public DfRetentionHandler dfRetentionHandler(DfRepoPort dfRepo, com.techeazyhq.cm.dfreg.dom.RecordsPurgePort records,
                                                 R2dbcDfTermsAdapter terms, AuditTrail audit,
                                                 @Value("${cm.dfreg.retention.consent-days:2555}") long days) {
        return new DfRetentionHandler(dfRepo, records, terms, audit, java.time.Clock.systemUTC(), java.time.Duration.ofDays(days));
    }

    @Bean
    public DfRetentionJob dfRetentionJob(DfRetentionHandler retention) {
        return new DfRetentionJob(retention);
    }

    @Bean
    public DfLeavingHandler dfLeavingHandler(DfRepoPort dfRepo, DfMemberDirectoryPort members,
                                             com.techeazyhq.cm.dfreg.dom.EmailPort email, AuditTrail audit,
                                             @Value("${cm.dfreg.offboarding.wind-down-days:30}") int windDownDays) {
        return new DfLeavingHandler(dfRepo, members, email, audit, java.time.Clock.systemUTC(), windDownDays);
    }

    @Bean
    public DfSuspensionHandler dfSuspensionHandler(DfRepoPort dfRepo, AuditTrail audit) {
        return new DfSuspensionHandler(dfRepo, audit, java.time.Clock.systemUTC());
    }

    @Bean
    public R2dbcDfTermsAdapter dfTermsAdapter(DatabaseClient db) {
        return new R2dbcDfTermsAdapter(db);
    }

    /** The versions of the consent manager's documents in force (ADR-063); bump one when the words change. */
    @Bean
    public DfTermsHandler dfTermsHandler(R2dbcDfTermsAdapter terms, AuditTrail audit,
                                         @Value("${cm.dfreg.terms.terms-version:2026-11}") String termsVersion,
                                         @Value("${cm.dfreg.terms.dpa-version:2026-11}") String dpaVersion) {
        return new DfTermsHandler(terms, java.util.Map.of(TermsDoc.TERMS, termsVersion, TermsDoc.DPA, dpaVersion), audit,
                java.time.Clock.systemUTC());
    }

    @Bean
    public NoncePurgeJob noncePurgeJob(R2dbcDfApiKeyAdapter nonces) {
        return new NoncePurgeJob(nonces);
    }

    @ConfigurationProperties(prefix = "cm.keycloak")
    public record KeycloakProps(String baseUrl, String realm, String adminUsername, String adminPassword) {
    }
}
