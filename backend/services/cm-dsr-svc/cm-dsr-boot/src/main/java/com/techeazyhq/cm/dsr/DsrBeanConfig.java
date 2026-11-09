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

package com.techeazyhq.cm.dsr;

import java.time.Clock;
import java.time.Duration;

import com.techeazyhq.cm.evtkit.PulsarClients;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.reactive.client.adapter.AdaptedReactivePulsarClientFactory;
import org.apache.pulsar.reactive.client.api.ReactivePulsarClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeazyhq.cm.audkit.AuditTrail;
import com.techeazyhq.cm.dsr.app.DpErasureHandler;
import com.techeazyhq.cm.dsr.app.DpReqHandler;
import com.techeazyhq.cm.dsr.app.DsrDeadlines;
import com.techeazyhq.cm.dsr.dom.DpCnsPort;
import com.techeazyhq.cm.dsr.dom.DpErasurePorts;
import com.techeazyhq.cm.dsr.dom.DpReqRepoPort;
import com.techeazyhq.cm.dsr.infra.KeycloakDpIdentityAdapter;
import com.techeazyhq.cm.dsr.infra.PulsarErasureSteps;
import com.techeazyhq.cm.dsr.infra.R2dbcDpErasureRepoAdapter;
import com.techeazyhq.cm.kckit.KeycloakAdminAuthFilter;
import com.techeazyhq.cm.dsr.infra.R2dbcDpReqRepoAdapter;
import com.techeazyhq.cm.dsr.infra.R2dbcDsrOutbox;
import com.techeazyhq.cm.dsr.infra.WebClientDpCnsAdapter;

@Configuration
public class DsrBeanConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public DpReqRepoPort dpReqRepoPort(DatabaseClient db) {
        return new R2dbcDpReqRepoAdapter(db);
    }

    /** Audited actions and messages for fiduciaries, both through this outbox. */
    @Bean
    public R2dbcDsrOutbox dsrOutbox(DatabaseClient db, ObjectMapper objectMapper, ReactivePulsarClient reactivePulsarClient) {
        return new R2dbcDsrOutbox(db, objectMapper, reactivePulsarClient);
    }

    @Bean
    public AuditTrail auditTrail(R2dbcDsrOutbox outbox) {
        return AuditTrail.to("dsr", outbox::enqueue);
    }

    @Bean
    public DpCnsPort dpCnsPort(WebClient.Builder builder, ReactiveCircuitBreakerFactory<?, ?> cbFactory,
                               @Value("${cm.service-urls.cns-qry:http://localhost:8083}") String cnsQryBaseUrl) {
        return new WebClientDpCnsAdapter(builder, cbFactory, cnsQryBaseUrl);
    }

    @Bean
    public DsrDeadlines dsrDeadlines(@Value("${cm.dsr.deadline-days.relay:90}") long relayDays,
                                     @Value("${cm.dsr.deadline-days.grievance:90}") long grievanceDays) {
        return new DsrDeadlines(Duration.ofDays(relayDays), Duration.ofDays(grievanceDays));
    }

    @Bean
    public DpReqHandler dpReqHandler(DpReqRepoPort reqs, DpCnsPort consents, R2dbcDsrOutbox outbox, AuditTrail audit,
                                     DsrDeadlines deadlines, Clock clock) {
        return new DpReqHandler(reqs, consents, outbox, audit, deadlines, clock);
    }

    @Bean
    public DpErasurePorts.Repo dpErasureRepo(DatabaseClient db, ObjectMapper objectMapper) {
        return new R2dbcDpErasureRepoAdapter(db, objectMapper);
    }

    @Bean
    public PulsarErasureSteps erasureSteps(ReactivePulsarClient reactivePulsarClient, ObjectMapper objectMapper) {
        return new PulsarErasureSteps(reactivePulsarClient, objectMapper);
    }

    /** The data principals' realm, through the Keycloak Admin API (as cm-stf-svc and cm-df-reg-svc reach theirs). */
    @Bean
    public DpErasurePorts.Identity dpIdentity(WebClient.Builder builder,
                                              @Value("${cm.keycloak.base-url:http://localhost:8080}") String baseUrl,
                                              @Value("${cm.keycloak.realm:cm-dp}") String realm,
                                              @Value("${cm.keycloak.admin-username:admin}") String adminUser,
                                              @Value("${cm.keycloak.admin-password:admin}") String adminPassword) {
        KeycloakAdminAuthFilter auth = new KeycloakAdminAuthFilter(builder.clone(), baseUrl, adminUser, adminPassword);
        return new KeycloakDpIdentityAdapter(builder.clone().baseUrl(baseUrl).filter(auth.filter()).build(), realm);
    }

    @Bean
    public DpErasureHandler dpErasureHandler(DpErasurePorts.Repo erasures, PulsarErasureSteps steps,
                                             DpErasurePorts.Identity identity, DpReqHandler requests, AuditTrail audit,
                                             Clock clock) {
        return new DpErasureHandler(erasures, steps, identity, requests, audit, clock);
    }

    @Bean(destroyMethod = "close")
    public PulsarClient pulsarClient(@Value("${cm.pulsar.service-url:pulsar://localhost:6650}") String serviceUrl)
            throws PulsarClientException {
        return PulsarClients.create(serviceUrl);
    }

    @Bean
    public ReactivePulsarClient reactivePulsarClient(PulsarClient pulsarClient) {
        return AdaptedReactivePulsarClientFactory.create(pulsarClient);
    }
}
