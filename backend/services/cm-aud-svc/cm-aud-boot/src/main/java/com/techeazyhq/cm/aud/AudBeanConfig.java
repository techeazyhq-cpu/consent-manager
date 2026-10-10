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

package com.techeazyhq.cm.aud;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeazyhq.cm.aud.app.AudAnchoringService;
import com.techeazyhq.cm.aud.app.AudLedgerService;
import com.techeazyhq.cm.aud.dom.AudCheckpointPort;
import com.techeazyhq.cm.aud.dom.AudLedgerPort;
import com.techeazyhq.cm.aud.dom.TimestampAuthorityPort;
import com.techeazyhq.cm.aud.infra.AudEvtParser;
import com.techeazyhq.cm.aud.infra.PulsarAudEvtConsumer;
import com.techeazyhq.cm.aud.infra.R2dbcAudCheckpointAdapter;
import com.techeazyhq.cm.aud.infra.R2dbcAudLedgerAdapter;
import com.techeazyhq.cm.aud.infra.Rfc3161TimestampAuthority;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.reactive.client.adapter.AdaptedReactivePulsarClientFactory;
import org.apache.pulsar.reactive.client.api.ReactivePulsarClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.reactive.TransactionalOperator;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;

/** Composition root (ADR-002) for the audit ledger service; scheduling drives the anchoring job (ADR-015). */
@Configuration
@EnableScheduling
public class AudBeanConfig {

    @Bean
    public AudLedgerPort audLedgerPort(DatabaseClient databaseClient, TransactionalOperator transactionalOperator) {
        return new R2dbcAudLedgerAdapter(databaseClient, transactionalOperator);
    }

    @Bean
    public AudLedgerService audLedgerService(AudLedgerPort audLedgerPort) {
        return new AudLedgerService(audLedgerPort, Clock.systemUTC());
    }

    @Bean
    public AudCheckpointPort audCheckpointPort(DatabaseClient databaseClient) {
        return new R2dbcAudCheckpointAdapter(databaseClient);
    }

    /** The external RFC 3161 timestamp authority checkpoints are anchored with (ADR-015). */
    @Bean
    public TimestampAuthorityPort timestampAuthorityPort(@Value("${cm.aud.anchoring.tsa-url}") String tsaUrl,
                                                         @Value("${cm.aud.anchoring.timeout:10s}") Duration timeout) {
        return new Rfc3161TimestampAuthority(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build(),
                URI.create(tsaUrl), timeout);
    }

    @Bean
    public AudAnchoringService audAnchoringService(AudLedgerPort audLedgerPort, AudCheckpointPort audCheckpointPort,
                                                   TimestampAuthorityPort timestampAuthorityPort) {
        return new AudAnchoringService(audLedgerPort, audCheckpointPort, timestampAuthorityPort, Clock.systemUTC());
    }

    @Bean(destroyMethod = "close")
    public PulsarClient pulsarClient(@Value("${cm.pulsar.service-url:pulsar://localhost:6650}") String serviceUrl)
            throws PulsarClientException {
        return PulsarClient.builder().serviceUrl(serviceUrl).build();
    }

    @Bean
    public ReactivePulsarClient reactivePulsarClient(PulsarClient pulsarClient) {
        return AdaptedReactivePulsarClientFactory.create(pulsarClient);
    }

    @Bean
    public PulsarAudEvtConsumer pulsarAudEvtConsumer(ReactivePulsarClient reactivePulsarClient, ObjectMapper objectMapper,
                                                     AudLedgerService audLedgerService) {
        return new PulsarAudEvtConsumer(reactivePulsarClient, new AudEvtParser(objectMapper), audLedgerService);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startConsumers(ApplicationReadyEvent event) {
        event.getApplicationContext().getBean(PulsarAudEvtConsumer.class).start();
    }
}
