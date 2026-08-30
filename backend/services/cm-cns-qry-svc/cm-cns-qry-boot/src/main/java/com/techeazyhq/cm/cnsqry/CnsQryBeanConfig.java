package com.techeazyhq.cm.cnsqry;

import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.reactive.client.adapter.AdaptedReactivePulsarClientFactory;
import org.apache.pulsar.reactive.client.api.ReactivePulsarClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.r2dbc.core.DatabaseClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeazyhq.cm.cnsqry.app.DpDashboardQryHandler;
import com.techeazyhq.cm.cnsqry.app.GetCnsArtQryHandler;
import com.techeazyhq.cm.cnsqry.app.GetCnsHstQryHandler;
import com.techeazyhq.cm.cnsqry.app.InternalStatusQryHandler;
import com.techeazyhq.cm.cnsqry.app.Projector;
import com.techeazyhq.cm.cnsqry.dom.CnsArtRdMdlRepoPort;
import com.techeazyhq.cm.cnsqry.dom.CnsHstRdMdlRepoPort;
import com.techeazyhq.cm.cnsqry.dom.PrjCkptPort;
import com.techeazyhq.cm.cnsqry.infra.PulsarCnsEvtConsumer;
import com.techeazyhq.cm.cnsqry.infra.R2dbcCnsArtRdMdlRepoAdapter;
import com.techeazyhq.cm.cnsqry.infra.R2dbcCnsHstRdMdlRepoAdapter;
import com.techeazyhq.cm.cnsqry.infra.R2dbcPrjCkptAdapter;

@Configuration
public class CnsQryBeanConfig {

    @Bean
    public CnsArtRdMdlRepoPort cnsArtRdMdlRepoPort(DatabaseClient db, ObjectMapper objectMapper) {
        return new R2dbcCnsArtRdMdlRepoAdapter(db, objectMapper);
    }

    @Bean
    public CnsHstRdMdlRepoPort cnsHstRdMdlRepoPort(DatabaseClient db) {
        return new R2dbcCnsHstRdMdlRepoAdapter(db);
    }

    @Bean
    public PrjCkptPort prjCkptPort(DatabaseClient db) {
        return new R2dbcPrjCkptAdapter(db);
    }

    @Bean
    public Projector projector(CnsArtRdMdlRepoPort rdMdlRepo, CnsHstRdMdlRepoPort hstRepo, PrjCkptPort ckptPort) {
        return new Projector(rdMdlRepo, hstRepo, ckptPort);
    }

    @Bean
    public GetCnsArtQryHandler getCnsArtQryHandler(CnsArtRdMdlRepoPort rdMdlRepo) {
        return new GetCnsArtQryHandler(rdMdlRepo);
    }

    @Bean
    public GetCnsHstQryHandler getCnsHstQryHandler(CnsHstRdMdlRepoPort hstRepo) {
        return new GetCnsHstQryHandler(hstRepo);
    }

    @Bean
    public DpDashboardQryHandler dpDashboardQryHandler(CnsArtRdMdlRepoPort rdMdlRepo) {
        return new DpDashboardQryHandler(rdMdlRepo);
    }

    @Bean
    public InternalStatusQryHandler internalStatusQryHandler(CnsArtRdMdlRepoPort rdMdlRepo) {
        return new InternalStatusQryHandler(rdMdlRepo);
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
    public PulsarCnsEvtConsumer pulsarCnsEvtConsumer(ReactivePulsarClient reactivePulsarClient, ObjectMapper objectMapper,
                                                       Projector projector) {
        return new PulsarCnsEvtConsumer(reactivePulsarClient, objectMapper, projector);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startConsumers(ApplicationReadyEvent event) {
        event.getApplicationContext().getBean(PulsarCnsEvtConsumer.class).start();
    }
}
