package com.techeazyhq.cm.cnscmd;

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
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.techeazyhq.cm.cnscmd.app.ApproveCnsCmdHandler;
import com.techeazyhq.cm.cnscmd.app.CnsExpirySweepJob;
import com.techeazyhq.cm.cnscmd.app.CnsLifecycleSagaOrchestrator;
import com.techeazyhq.cm.cnscmd.app.CreateCnsReqCmdHandler;
import com.techeazyhq.cm.cnscmd.app.DenyCnsReqCmdHandler;
import com.techeazyhq.cm.cnscmd.app.LinkDpCmdHandler;
import com.techeazyhq.cm.cnscmd.app.RevokeCnsArtCmdHandler;
import com.techeazyhq.cm.cnscmd.dom.CnsArtRepoPort;
import com.techeazyhq.cm.cnscmd.dom.CnsReqRepoPort;
import com.techeazyhq.cm.cnscmd.dom.DfActivePort;
import com.techeazyhq.cm.cnscmd.dom.DfSubEligiblePolicy;
import com.techeazyhq.cm.cnscmd.dom.NtfCmdPort;
import com.techeazyhq.cm.cnscmd.dom.ObxPort;
import com.techeazyhq.cm.cnscmd.infra.ObxRelay;
import com.techeazyhq.cm.cnscmd.infra.PulsarCnsEvtProducer;
import com.techeazyhq.cm.cnscmd.infra.PulsarCnsSagaEvtConsumer;
import com.techeazyhq.cm.cnscmd.infra.PulsarNtfCmdAdapter;
import com.techeazyhq.cm.cnscmd.infra.R2dbcCnsArtRepoAdapter;
import com.techeazyhq.cm.cnscmd.infra.R2dbcCnsReqRepoAdapter;
import com.techeazyhq.cm.cnscmd.infra.R2dbcObxAdapter;
import com.techeazyhq.cm.cnscmd.infra.WebClientDfActiveAdapter;
import com.techeazyhq.cm.cnscmd.infra.WebClientDfSubEligibleAdapter;
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

    @Bean
    public ObxRelay obxRelay(DatabaseClient db, PulsarCnsEvtProducer producer) {
        return new ObxRelay(db, producer);
    }

    // ---- CnsTkn signing (plan §7c step 3, §9a) ----

    @Bean
    public RSAKey cnsTknSigningKey() throws com.nimbusds.jose.JOSEException {
        // Phase-1 dev default: ephemeral key generated at boot (plan §7c note: "dev: generated
        // at boot / stored in a jwks.json in phase 1; real KMS deferred", §16 roadmap).
        return new RSAKeyGenerator(2048).keyID("cm-cns-cmd-dev").generate();
    }

    @Bean
    public CnsTknSigner cnsTknSigner(RSAKey cnsTknSigningKey) {
        return new CnsTknSigner(cnsTknSigningKey);
    }

    // ---- Circuit-Breaker-wrapped calls to sibling services (plan §7b, §7c step 1) ----

    @Bean
    public DfActivePort dfActivePort(WebClient.Builder builder, ReactiveCircuitBreakerFactory cbFactory,
                                      ServiceUrls serviceUrls) {
        return new WebClientDfActiveAdapter(builder, cbFactory, serviceUrls.dfReg());
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
        return PulsarClient.builder().serviceUrl(serviceUrl).build();
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
                                                           DfSubEligiblePolicy dfSubEligiblePolicy) {
        return new CreateCnsReqCmdHandler(cnsReqRepo, dfActivePort, dfSubEligiblePolicy);
    }

    @Bean
    public ApproveCnsCmdHandler approveCnsCmdHandler(CnsReqRepoPort cnsReqRepo, CnsArtRepoPort cnsArtRepo,
                                                       ObxPort obxPort, CnsTknSigner cnsTknSigner) {
        return new ApproveCnsCmdHandler(cnsReqRepo, cnsArtRepo, obxPort, cnsTknSigner);
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
    public CnsLifecycleSagaOrchestrator cnsLifecycleSagaOrchestrator(CnsReqRepoPort cnsReqRepo, NtfCmdPort ntfCmdPort) {
        return new CnsLifecycleSagaOrchestrator(cnsReqRepo, ntfCmdPort);
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

    @EventListener(ApplicationReadyEvent.class)
    public void startSagaConsumer(ApplicationReadyEvent event) {
        event.getApplicationContext().getBean(PulsarCnsSagaEvtConsumer.class).start();
    }

    @ConfigurationProperties(prefix = "cm.service-urls")
    public record ServiceUrls(String dfReg, String bil) {
    }
}
