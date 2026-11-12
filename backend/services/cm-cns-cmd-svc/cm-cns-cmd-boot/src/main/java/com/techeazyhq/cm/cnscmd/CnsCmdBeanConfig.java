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

package com.techeazyhq.cm.cnscmd;

import com.techeazyhq.cm.evtkit.PulsarClients;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.reactive.client.adapter.AdaptedReactivePulsarClientFactory;
import org.apache.pulsar.reactive.client.api.ReactivePulsarClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeazyhq.cm.cnscmd.app.Webhooks;
import com.techeazyhq.cm.cnscmd.app.WhkDeliveryWorker;
import com.techeazyhq.cm.cnscmd.dom.SecretSealerPort;
import com.techeazyhq.cm.cnscmd.dom.WhkDelivery;
import com.techeazyhq.cm.cnscmd.dom.WhkRepoPort;
import com.techeazyhq.cm.cnscmd.dom.WhkSenderPort;
import com.techeazyhq.cm.cnscmd.infra.AesGcmSecretSealer;
import com.techeazyhq.cm.cnscmd.infra.R2dbcWhkRepoAdapter;
import com.techeazyhq.cm.cnscmd.infra.WebClientWhkSender;
import com.techeazyhq.cm.cnscmd.app.CnsRcptJob;
import com.techeazyhq.cm.cnscmd.app.CnsRecDoc;
import com.techeazyhq.cm.cnscmd.app.CnsRecords;
import com.techeazyhq.cm.cnscmd.dom.CnsRecRepoPort;
import com.techeazyhq.cm.cnscmd.dom.DfNamePort;
import com.techeazyhq.cm.cnscmd.dom.RcptSignerPort;
import com.techeazyhq.cm.cnscmd.infra.JwsRcptSigner;
import com.techeazyhq.cm.cnscmd.infra.R2dbcCnsRecRepoAdapter;
import com.techeazyhq.cm.cnscmd.infra.WebClientDfNameAdapter;
import com.techeazyhq.cm.cnscmd.app.ChangeCnsArtCmds;
import com.techeazyhq.cm.cnscmd.app.CnsReqTerms;
import com.techeazyhq.cm.cnscmd.app.PrpsDefCmds;
import com.techeazyhq.cm.cnscmd.app.PrpsResolver;
import com.techeazyhq.cm.cnscmd.dom.PrpsDefRepoPort;
import com.techeazyhq.cm.cnscmd.infra.R2dbcPrpsDefRepoAdapter;
import com.techeazyhq.cm.audkit.AuditTrail;
import com.techeazyhq.cm.cnscmd.app.SubmitCnsReqBatchHandler;
import com.techeazyhq.cm.cnscmd.app.CnsReqBatchWorker;
import com.techeazyhq.cm.cnscmd.app.CnsReqBatchSender;
import com.techeazyhq.cm.cnscmd.app.CnsReqBatchQueries;
import com.techeazyhq.cm.cnscmd.app.ApproveCnsCmdHandler;
import com.techeazyhq.cm.cnscmd.app.CnsExpirySweepJob;
import com.techeazyhq.cm.cnscmd.app.CnsTknIssuer;
import com.techeazyhq.cm.cnscmd.app.GetCnsArtLiveStatusQryHandler;
import com.techeazyhq.cm.cnscmd.app.RefreshCnsTknCmdHandler;
import com.techeazyhq.cm.cnscmd.app.CnsLifecycleSagaOrchestrator;
import com.techeazyhq.cm.cnscmd.app.SgaRedriveJob;
import com.techeazyhq.cm.cnscmd.app.CreateCnsReqCmdHandler;
import com.techeazyhq.cm.cnscmd.app.DenyCnsReqCmdHandler;
import com.techeazyhq.cm.cnscmd.app.LinkDpCmdHandler;
import com.techeazyhq.cm.cnscmd.app.RevokeCnsArtCmdHandler;
import com.techeazyhq.cm.cnscmd.dom.DfUsagePort;
import com.techeazyhq.cm.cnscmd.dom.CnsReqBatchRepoPort;
import com.techeazyhq.cm.cnscmd.dom.CnsArtRepoPort;
import com.techeazyhq.cm.cnscmd.dom.CnsReqRepoPort;
import com.techeazyhq.cm.cnscmd.dom.SgaStatePort;
import com.techeazyhq.cm.cnscmd.dom.ContactPolicy;
import com.techeazyhq.cm.cnscmd.dom.DfActivePort;
import com.techeazyhq.cm.cnscmd.dom.DfSubEligiblePolicy;
import com.techeazyhq.cm.cnscmd.dom.NtfCmdPort;
import com.techeazyhq.cm.cnscmd.dom.ObxPort;
import com.techeazyhq.cm.cnscmd.infra.WebClientDfUsageAdapter;
import com.techeazyhq.cm.cnscmd.infra.R2dbcCnsReqBatchRepoAdapter;
import com.techeazyhq.cm.cnscmd.infra.ObxRelay;
import com.techeazyhq.cm.cnscmd.infra.PulsarCnsEvtProducer;
import com.techeazyhq.cm.cnscmd.infra.PulsarCnsSagaEvtConsumer;
import com.techeazyhq.cm.cnscmd.infra.PulsarDfMessageConsumer;
import com.techeazyhq.cm.cnscmd.infra.PulsarNtfCmdAdapter;
import com.techeazyhq.cm.cnscmd.infra.R2dbcCnsArtRepoAdapter;
import com.techeazyhq.cm.cnscmd.infra.R2dbcCnsReqRepoAdapter;
import com.techeazyhq.cm.cnscmd.infra.R2dbcObxAdapter;
import com.techeazyhq.cm.cnscmd.infra.R2dbcSgaStateAdapter;
import com.techeazyhq.cm.cnscmd.infra.WebClientDfActiveAdapter;
import com.techeazyhq.cm.cnscmd.infra.WebClientDfSubEligibleAdapter;
import com.techeazyhq.cm.seckit.CnsTknKeys;
import com.techeazyhq.cm.seckit.CnsTknSigner;

/**
 * Composition root wiring ports (plan §5's -dom) to adapters (plan §5's -infra), and adapters
 * to the -app command handlers they're injected into. Everything below is boot-module-only —
 * no other layer of this service knows Spring, R2DBC, WebClient, or Pulsar exist.
 */
@Configuration
@EnableConfigurationProperties(CnsCmdBeanConfig.ServiceUrls.class)
public class CnsCmdBeanConfig {

    // ---- persistence ports ----

    @Bean
    public CnsReqRepoPort cnsReqRepoPort(DatabaseClient db, ObjectMapper objectMapper) {
        return new R2dbcCnsReqRepoAdapter(db, objectMapper);
    }

    @Bean
    public CnsArtRepoPort cnsArtRepoPort(DatabaseClient db, ObjectMapper objectMapper) {
        return new R2dbcCnsArtRepoAdapter(db, objectMapper);
    }

    @Bean
    public ObxPort obxPort(DatabaseClient db, ObjectMapper objectMapper) {
        return new R2dbcObxAdapter(db, objectMapper);
    }

    /** What people and the batch worker do, through the outbox to the audit ledger (ADR-040). */
    @Bean
    public AuditTrail auditTrail(ObxPort obxPort) {
        return AuditTrail.to("cnscmd", evt -> obxPort.enqueue(evt, "PlatformAction", evt.target() != null ? evt.target() : "cnscmd"));
    }

    @Bean
    public ObxRelay obxRelay(DatabaseClient db, PulsarCnsEvtProducer producer) {
        return new ObxRelay(db, producer);
    }

    // ---- CnsTkn signing (plan §7c step 3, §9a) ----

    /**
     * Persistent signing keys (ADR-006): created on first start when allowed, then reused, so tokens stay verifiable
     * across restarts. Production mounts a managed key set at {@code cm.cns-tkn.keys-path} and turns creation off.
     */
    @Bean
    public CnsTknKeys cnsTknKeys(@Value("${cm.cns-tkn.keys-path}") String keysPath,
                                 @Value("${cm.cns-tkn.create-keys-if-missing:true}") boolean createIfMissing)
            throws java.io.IOException {
        return CnsTknKeys.loadOrCreate(java.nio.file.Path.of(keysPath), createIfMissing, java.time.Clock.systemUTC());
    }

    @Bean
    public CnsTknSigner cnsTknSigner(CnsTknKeys cnsTknKeys) {
        return new CnsTknSigner(cnsTknKeys);
    }

    // ---- Circuit-Breaker-wrapped calls to sibling services (plan §7b, §7c step 1) ----

    @Bean
    public DfActivePort dfActivePort(WebClient.Builder builder, ReactiveCircuitBreakerFactory cbFactory,
                                      ServiceUrls serviceUrls) {
        return new WebClientDfActiveAdapter(builder, cbFactory, serviceUrls.dfReg());
    }

    @Bean
    public DfUsagePort dfUsagePort(WebClient.Builder builder, ReactiveCircuitBreakerFactory cbFactory,
                                   ServiceUrls serviceUrls) {
        return new WebClientDfUsageAdapter(builder, cbFactory, serviceUrls.bil());
    }

    /** Purposes and their versions (ADR-031), and the check every request makes against them. */
    @Bean
    public PrpsDefRepoPort prpsDefRepoPort(DatabaseClient db, ObjectMapper objectMapper) {
        return new R2dbcPrpsDefRepoAdapter(db, objectMapper);
    }

    /**
     * The model that drafts purposes (ADR-111), as platform staff set it in CM Admin (ADR-113). Until they save a setting the
     * cm.cnscmd.ai-drafting.* properties apply: off unless enabled, and then an OpenAI-compatible endpoint the operator runs (Ollama, vLLM,
     * llama.cpp). Keys are secrets: sealed when stored, never logged.
     */
    @Bean
    public com.techeazyhq.cm.cnscmd.dom.DraftingModelFactoryPort draftingModelFactory(WebClient.Builder builder) {
        return new com.techeazyhq.cm.cnscmd.infra.DraftingModels(builder);
    }

    @Bean
    public com.techeazyhq.cm.cnscmd.dom.AiDraftingConfigRepoPort aiDraftingConfigRepoPort(DatabaseClient db) {
        return new com.techeazyhq.cm.cnscmd.infra.R2dbcAiDraftingConfigAdapter(db);
    }

    @Bean
    public com.techeazyhq.cm.cnscmd.app.AiDraftingSettings draftingModel(com.techeazyhq.cm.cnscmd.dom.AiDraftingConfigRepoPort repo,
            com.techeazyhq.cm.cnscmd.dom.DraftingModelFactoryPort factory, SecretSealerPort sealer, AuditTrail audit,
            @Value("${cm.cnscmd.ai-drafting.enabled:false}") boolean enabled,
            @Value("${cm.cnscmd.ai-drafting.endpoint:http://localhost:11434}") String endpoint,
            @Value("${cm.cnscmd.ai-drafting.model:llama3.2:3b}") String model,
            @Value("${cm.cnscmd.ai-drafting.api-key:}") String apiKey,
            @Value("${cm.cnscmd.ai-drafting.timeout-seconds:100}") int timeoutSeconds,
            @Value("${cm.cnscmd.ai-drafting.max-tokens:1500}") int maxTokens) {
        // the properties name an OpenAI-compatible server; being the operator's own configuration they carry their own consent to where it points
        com.techeazyhq.cm.cnscmd.dom.AiDraftingConfig fromProperties = new com.techeazyhq.cm.cnscmd.dom.AiDraftingConfig(enabled,
                com.techeazyhq.cm.cnscmd.dom.AiProvider.OPENAI_COMPATIBLE, endpoint, model, null, timeoutSeconds, maxTokens, true, "properties", null);
        com.techeazyhq.cm.cnscmd.app.AiDraftingSettings settings = new com.techeazyhq.cm.cnscmd.app.AiDraftingSettings(repo, factory, sealer, audit,
                java.time.Clock.systemUTC(), fromProperties, apiKey);
        settings.reload().block(java.time.Duration.ofSeconds(10)); // a saved setting wins from the first request
        return settings;
    }

    /** Picks up a change made on another instance. */
    @Bean
    public com.techeazyhq.cm.cnscmd.app.AiDraftingReloadJob aiDraftingReloadJob(com.techeazyhq.cm.cnscmd.app.AiDraftingSettings settings) {
        return new com.techeazyhq.cm.cnscmd.app.AiDraftingReloadJob(settings);
    }

    @Bean
    public com.techeazyhq.cm.cnscmd.app.PrpsDraftAssist prpsDraftAssist(com.techeazyhq.cm.cnscmd.app.AiDraftingSettings model, AuditTrail audit,
            ObjectMapper objectMapper,
            @Value("${cm.cnscmd.ai-drafting.max-input-chars:2000}") int maxInput,
            @Value("${cm.cnscmd.ai-drafting.per-hour:20}") int perHour,
            @Value("${cm.cnscmd.ai-drafting.max-concurrent:2}") int maxConcurrent) {
        return new com.techeazyhq.cm.cnscmd.app.PrpsDraftAssist(model, audit, objectMapper, java.time.Clock.systemUTC(), maxInput, perHour, maxConcurrent);
    }

    @Bean
    public PrpsDefCmds prpsDefCmds(PrpsDefRepoPort prpsDefRepo,
                                   com.techeazyhq.cm.cnscmd.dom.ProcessorRepoPort processorRepo, AuditTrail audit) {
        return new PrpsDefCmds(prpsDefRepo, processorRepo, audit, java.time.Clock.systemUTC());
    }

    /** A fiduciary's register of processors, which purposes name (ADR-075). */
    @Bean
    public com.techeazyhq.cm.cnscmd.dom.ProcessorRepoPort processorRepoPort(DatabaseClient db) {
        return new com.techeazyhq.cm.cnscmd.infra.R2dbcProcessorRepoAdapter(db);
    }

    @Bean
    public com.techeazyhq.cm.cnscmd.app.ProcessorCmds processorCmds(
            com.techeazyhq.cm.cnscmd.dom.ProcessorRepoPort processorRepo, PrpsDefRepoPort prpsDefRepo, AuditTrail audit) {
        return new com.techeazyhq.cm.cnscmd.app.ProcessorCmds(processorRepo, prpsDefRepo, audit, java.time.Clock.systemUTC());
    }

    @Bean
    public PrpsResolver prpsResolver(PrpsDefRepoPort prpsDefRepo) {
        return new PrpsResolver(prpsDefRepo);
    }

    @Bean
    public CnsReqBatchRepoPort cnsReqBatchRepoPort(DatabaseClient db, ObjectMapper objectMapper) {
        return new R2dbcCnsReqBatchRepoAdapter(db, objectMapper);
    }

    /** In the sandbox (ADR-037), only test contacts can be asked: it never holds a real person's address. */
    @Bean
    public ContactPolicy contactPolicy(@Value("${cm.sandbox.enabled:false}") boolean sandbox) {
        return ContactPolicy.of(sandbox);
    }

    @Bean
    public SubmitCnsReqBatchHandler submitCnsReqBatchHandler(CnsReqBatchRepoPort batchRepo, DfActivePort dfActivePort,
                                                             DfUsagePort dfUsagePort, PrpsResolver prpsResolver,
                                                             ContactPolicy contactPolicy, AuditTrail audit) {
        return new SubmitCnsReqBatchHandler(batchRepo, dfActivePort, dfUsagePort, prpsResolver, contactPolicy, audit);
    }

    /** A bean of its own, so its @Transactional send wraps each row's request and outcome together. */
    @Bean
    public CnsReqBatchSender cnsReqBatchSender(CnsReqRepoPort cnsReqRepo, CnsReqBatchRepoPort batchRepo) {
        return new CnsReqBatchSender(cnsReqRepo, batchRepo);
    }

    @Bean
    public CnsReqBatchWorker cnsReqBatchWorker(CnsReqBatchRepoPort batchRepo, CnsReqBatchSender sender,
                                               DfUsagePort dfUsagePort, AuditTrail audit) {
        return new CnsReqBatchWorker(batchRepo, sender, dfUsagePort, audit);
    }

    @Bean
    public CnsReqBatchQueries cnsReqBatchQueries(CnsReqBatchRepoPort batchRepo) {
        return new CnsReqBatchQueries(batchRepo);
    }

    @Bean
    public DfSubEligiblePolicy dfSubEligiblePolicy(WebClient.Builder builder, ReactiveCircuitBreakerFactory cbFactory,
                                                     ServiceUrls serviceUrls) {
        return new WebClientDfSubEligibleAdapter(builder, cbFactory, serviceUrls.bil());
    }

    // ---- Pulsar (plan §6/§13 — user-requested swap from Kafka) ----

    @Bean(destroyMethod = "close")
    public PulsarClient pulsarClient(@Value("${cm.pulsar.service-url:pulsar://localhost:6650}") String serviceUrl)
            throws org.apache.pulsar.client.api.PulsarClientException {
        return PulsarClients.create(serviceUrl);
    }

    @Bean
    public ReactivePulsarClient reactivePulsarClient(PulsarClient pulsarClient) {
        return AdaptedReactivePulsarClientFactory.create(pulsarClient);
    }

    @Bean
    public PulsarCnsEvtProducer pulsarCnsEvtProducer(ReactivePulsarClient reactivePulsarClient) {
        return new PulsarCnsEvtProducer(reactivePulsarClient);
    }

    @Bean
    public NtfCmdPort ntfCmdPort(PulsarCnsEvtProducer producer, ObjectMapper objectMapper) {
        return new PulsarNtfCmdAdapter(producer, objectMapper);
    }

    // ---- command handlers + saga + scheduled jobs ----

    @Bean
    public CreateCnsReqCmdHandler createCnsReqCmdHandler(CnsReqRepoPort cnsReqRepo, DfActivePort dfActivePort,
                                                           DfSubEligiblePolicy dfSubEligiblePolicy,
                                                           PrpsResolver prpsResolver, ContactPolicy contactPolicy) {
        return new CreateCnsReqCmdHandler(cnsReqRepo, dfActivePort, dfSubEligiblePolicy, prpsResolver, contactPolicy);
    }

    @Bean
    public CnsTknIssuer cnsTknIssuer(CnsTknSigner cnsTknSigner,
                                     @Value("${cm.cns-tkn.lifetime:PT15M}") java.time.Duration lifetime,
                                     com.techeazyhq.cm.cnscmd.dom.StatusSlotPort statusSlots,
                                     @Value("${cm.cns-tkn.status-list-base-url:}") String statusListBaseUrl) {
        return new CnsTknIssuer(cnsTknSigner, lifetime, java.time.Clock.systemUTC(), statusSlots, statusListBaseUrl);
    }

    /** Where each consent token's withdrawal can be seen without asking about it (ADR-077). */
    @Bean
    public com.techeazyhq.cm.cnscmd.dom.StatusSlotPort statusSlotPort(DatabaseClient db) {
        return new com.techeazyhq.cm.cnscmd.infra.R2dbcStatusSlotAdapter(db);
    }

    @Bean
    public com.techeazyhq.cm.cnscmd.app.StatusLists statusLists(com.techeazyhq.cm.cnscmd.dom.StatusSlotPort statusSlots,
                                                                CnsTknSigner cnsTknSigner,
                                                                @Value("${cm.cns-tkn.status-list-base-url:}") String baseUrl,
                                                                @Value("${cm.cns-tkn.status-list-ttl:PT60S}") java.time.Duration ttl,
                                                                @Value("${cm.cns-tkn.status-list-cache:PT10S}") java.time.Duration cacheFor) {
        return new com.techeazyhq.cm.cnscmd.app.StatusLists(statusSlots, cnsTknSigner, baseUrl, ttl, cacheFor,
                java.time.Clock.systemUTC());
    }

    @Bean
    public RefreshCnsTknCmdHandler refreshCnsTknCmdHandler(CnsArtRepoPort cnsArtRepo, CnsTknIssuer cnsTknIssuer) {
        return new RefreshCnsTknCmdHandler(cnsArtRepo, cnsTknIssuer, java.time.Clock.systemUTC());
    }

    @Bean
    public GetCnsArtLiveStatusQryHandler getCnsArtLiveStatusQryHandler(CnsArtRepoPort cnsArtRepo) {
        return new GetCnsArtLiveStatusQryHandler(cnsArtRepo, java.time.Clock.systemUTC());
    }

    /** A request's purpose version: what the decision screen shows and the grant is checked against (ADR-026). */
    @Bean
    public CnsReqTerms cnsReqTerms(CnsReqRepoPort cnsReqRepo, PrpsDefRepoPort prpsDefRepo) {
        return new CnsReqTerms(cnsReqRepo, prpsDefRepo);
    }

    // ---- fiduciary webhooks (ADR-047) ----

    @Bean
    public WhkRepoPort whkRepoPort(DatabaseClient db, ObjectMapper objectMapper) {
        return new R2dbcWhkRepoAdapter(db, objectMapper);
    }

    /** Private addresses only where explicitly allowed, for local development; never in production (SSRF). */
    @Bean
    public WhkSenderPort whkSenderPort(WebClient.Builder builder,
                                       @Value("${cm.webhooks.allow-private:false}") boolean allowPrivate) {
        return new WebClientWhkSender(builder, allowPrivate);
    }

    @Bean
    public SecretSealerPort cnsCmdSecretSealer(@Value("${cm.cnscmd.secrets-key}") String key) {
        return new AesGcmSecretSealer(key);
    }

    @Bean
    public Webhooks webhooks(WhkRepoPort repo, WhkSenderPort sender, SecretSealerPort sealer, AuditTrail audit,
                             @Value("${cm.webhooks.allow-private:false}") boolean allowPrivate) {
        // plain http only where private addresses are allowed: local development
        return new Webhooks(repo, sender, sealer, audit, java.time.Clock.systemUTC(), allowPrivate);
    }

    @Bean
    public WhkDeliveryWorker whkDeliveryWorker(WhkRepoPort repo, WhkSenderPort sender, SecretSealerPort sealer,
                                               @Value("${cm.webhooks.retry-delays:}") java.util.List<java.time.Duration> delays) {
        return new WhkDeliveryWorker(repo, sender, sealer, java.time.Clock.systemUTC(),
                delays == null || delays.isEmpty() ? WhkDelivery.RETRY_DELAYS : delays);
    }

    // ---- consent records and receipts (ADR-025) ----

    @Bean
    public CnsRecRepoPort cnsRecRepoPort(DatabaseClient db, ObjectMapper objectMapper) {
        return new R2dbcCnsRecRepoAdapter(db, objectMapper);
    }

    /** The receipt keys: a set of their own, never the token keys (finding R8), created on first start when allowed. */
    @Bean
    public RcptSignerPort rcptSignerPort(@Value("${cm.cns-rcpt.keys-path}") String keysPath,
                                         @Value("${cm.cns-rcpt.create-keys-if-missing:true}") boolean createIfMissing,
                                         ObjectMapper objectMapper) throws java.io.IOException {
        return new JwsRcptSigner(CnsTknKeys.loadOrCreate(java.nio.file.Path.of(keysPath), createIfMissing,
                java.time.Clock.systemUTC(), "cns-rcpt-"), objectMapper);
    }

    @Bean
    public DfNamePort dfNamePort(WebClient.Builder builder, ReactiveCircuitBreakerFactory cbFactory,
                                 ServiceUrls serviceUrls) {
        return new WebClientDfNameAdapter(builder, cbFactory, serviceUrls.dfReg());
    }

    @Bean
    public CnsRecords cnsRecords(CnsRecRepoPort recs, PrpsDefRepoPort prpsDefs, DfNamePort dfNames,
                                 RcptSignerPort signer, ObxPort obxPort,
                                 @Value("${cm.platform.name}") String name,
                                 @Value("${cm.platform.jurisdiction}") String jurisdiction,
                                 @Value("${cm.platform.grievance-contact}") String grievanceContact,
                                 @Value("${cm.platform.withdrawal-url}") String withdrawalUrl) {
        return new CnsRecords(recs, prpsDefs, dfNames, signer, obxPort,
                new CnsRecDoc.Platform(name, jurisdiction, grievanceContact, withdrawalUrl), java.time.Clock.systemUTC());
    }

    @Bean
    public CnsRcptJob cnsRcptJob(CnsRecRepoPort recs, CnsRecords records) {
        return new CnsRcptJob(recs, records);
    }

    @Bean
    public ChangeCnsArtCmds changeCnsArtCmds(CnsArtRepoPort cnsArtRepo, CnsReqRepoPort cnsReqRepo,
                                             CnsReqTerms cnsReqTerms) {
        return new ChangeCnsArtCmds(cnsArtRepo, cnsReqRepo, cnsReqTerms, java.time.Clock.systemUTC());
    }

    @Bean
    public ApproveCnsCmdHandler approveCnsCmdHandler(CnsReqRepoPort cnsReqRepo, CnsArtRepoPort cnsArtRepo,
                                                       ObxPort obxPort, CnsTknIssuer cnsTknIssuer,
                                                       CnsReqTerms cnsReqTerms) {
        return new ApproveCnsCmdHandler(cnsReqRepo, cnsArtRepo, obxPort, cnsTknIssuer, cnsReqTerms);
    }

    @Bean
    public DenyCnsReqCmdHandler denyCnsReqCmdHandler(CnsReqRepoPort cnsReqRepo) {
        return new DenyCnsReqCmdHandler(cnsReqRepo);
    }

    @Bean
    public RevokeCnsArtCmdHandler revokeCnsArtCmdHandler(CnsArtRepoPort cnsArtRepo) {
        return new RevokeCnsArtCmdHandler(cnsArtRepo);
    }

    @Bean
    public LinkDpCmdHandler linkDpCmdHandler(CnsReqRepoPort cnsReqRepo, CnsArtRepoPort cnsArtRepo, ObxPort obxPort) {
        return new LinkDpCmdHandler(cnsReqRepo, cnsArtRepo, obxPort);
    }

    @Bean
    public SgaStatePort sgaStatePort(DatabaseClient db) {
        return new R2dbcSgaStateAdapter(db);
    }

    @Bean
    public CnsLifecycleSagaOrchestrator cnsLifecycleSagaOrchestrator(CnsReqRepoPort cnsReqRepo, NtfCmdPort ntfCmdPort,
                                                                     SgaStatePort sgaStatePort) {
        return new CnsLifecycleSagaOrchestrator(cnsReqRepo, ntfCmdPort, sgaStatePort, java.time.Clock.systemUTC());
    }

    /** Runs again the saga legs left unfinished (ADR-005). */
    @Bean
    public SgaRedriveJob sgaRedriveJob(SgaStatePort sgaStatePort, CnsLifecycleSagaOrchestrator orchestrator,
                                       @Value("${cm.cnscmd.saga-redrive.stale-after:PT5M}") java.time.Duration staleAfter,
                                       @Value("${cm.cnscmd.saga-redrive.max-attempts:10}") int maxAttempts) {
        return new SgaRedriveJob(sgaStatePort, orchestrator, staleAfter, maxAttempts, java.time.Clock.systemUTC());
    }

    @Bean
    public CnsExpirySweepJob cnsExpirySweepJob(CnsArtRepoPort cnsArtRepo) {
        return new CnsExpirySweepJob(cnsArtRepo);
    }

    @Bean
    public PulsarCnsSagaEvtConsumer pulsarCnsSagaEvtConsumer(ReactivePulsarClient reactivePulsarClient,
                                                              ObjectMapper objectMapper,
                                                              CnsLifecycleSagaOrchestrator orchestrator) {
        return new PulsarCnsSagaEvtConsumer(reactivePulsarClient, objectMapper, orchestrator);
    }

    /** Principal requests relayed by cm-dsr-svc, to the fiduciary's webhooks (ADR-058). */
    @Bean
    public PulsarDfMessageConsumer pulsarDfMessageConsumer(ReactivePulsarClient reactivePulsarClient,
                                                           ObjectMapper objectMapper, WhkRepoPort webhooks) {
        return new PulsarDfMessageConsumer(reactivePulsarClient, objectMapper, webhooks);
    }

    /** Closing a principal's account (ADR-059): this context's step. */
    @Bean
    public com.techeazyhq.cm.cnscmd.app.EraseDpConsentsHandler eraseDpConsentsHandler(CnsReqRepoPort reqs,
            CnsArtRepoPort arts, LinkDpCmdHandler link, DatabaseClient db) {
        return new com.techeazyhq.cm.cnscmd.app.EraseDpConsentsHandler(reqs, arts, link,
                new com.techeazyhq.cm.cnscmd.infra.R2dbcDpContactErasureAdapter(db));
    }

    /** A closed fiduciary's records past their retention (ADR-064). */
    @Bean
    public com.techeazyhq.cm.cnscmd.app.PurgeDfRecordsHandler purgeDfRecordsHandler(DatabaseClient db,
            org.springframework.transaction.reactive.TransactionalOperator tx) {
        return new com.techeazyhq.cm.cnscmd.app.PurgeDfRecordsHandler(
                new com.techeazyhq.cm.cnscmd.infra.R2dbcDfRecordsPurgeAdapter(db, tx));
    }

    /** A fiduciary closing (ADR-064): this context's step. */
    @Bean
    public com.techeazyhq.cm.cnscmd.app.CloseDfConsentsHandler closeDfConsentsHandler(CnsReqRepoPort reqs,
            CnsArtRepoPort arts, DatabaseClient db) {
        return new com.techeazyhq.cm.cnscmd.app.CloseDfConsentsHandler(reqs, arts,
                new com.techeazyhq.cm.cnscmd.infra.R2dbcDpContactErasureAdapter(db));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startSagaConsumer(ApplicationReadyEvent event) {
        event.getApplicationContext().getBean(PulsarCnsSagaEvtConsumer.class).start();
        event.getApplicationContext().getBean(PulsarDfMessageConsumer.class).start();
        com.techeazyhq.cm.cnscmd.app.EraseDpConsentsHandler erase =
                event.getApplicationContext().getBean(com.techeazyhq.cm.cnscmd.app.EraseDpConsentsHandler.class);
        com.techeazyhq.cm.evtkit.ErasureSteps.serve(event.getApplicationContext().getBean(ReactivePulsarClient.class),
                event.getApplicationContext().getBean(ObjectMapper.class), "cnscmd",
                com.techeazyhq.cm.evtkit.ErasureSteps.CONSENTS, cmd -> erase.handle(cmd.dpId(), cmd.email()));
    }

    @ConfigurationProperties(prefix = "cm.service-urls")
    public record ServiceUrls(String dfReg, String bil) {
    }
}
