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

package com.techeazyhq.cm.bil;

import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.reactive.client.adapter.AdaptedReactivePulsarClientFactory;
import org.apache.pulsar.reactive.client.api.ReactivePulsarClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.r2dbc.core.DatabaseClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeazyhq.cm.bil.app.AssignSubPlnCmdHandler;
import com.techeazyhq.cm.bil.app.BilOverviewQryHandler;
import com.techeazyhq.cm.bil.app.PlnChgReqHandler;
import com.techeazyhq.cm.bil.dom.PlnChgReqRepoPort;
import com.techeazyhq.cm.bil.infra.R2dbcPlnChgReqRepoAdapter;
import com.techeazyhq.cm.bil.app.GetDfSubStatusQryHandler;
import com.techeazyhq.cm.bil.app.GetUsgQryHandler;
import com.techeazyhq.cm.bil.app.ListSubPlnsQryHandler;
import com.techeazyhq.cm.bil.app.OnDfActivatedEvtHandler;
import com.techeazyhq.cm.bil.app.SetDfSubStsCmdHandler;
import com.techeazyhq.cm.bil.app.SubEligQryHandler;
import com.techeazyhq.cm.bil.app.UsgReservationHandler;
import com.techeazyhq.cm.bil.dom.DfSubRepoPort;
import com.techeazyhq.cm.bil.dom.SubPlnRepoPort;
import com.techeazyhq.cm.bil.dom.UsgPort;
import com.techeazyhq.cm.bil.infra.PulsarDfActivatedConsumer;
import com.techeazyhq.cm.bil.infra.R2dbcDfSubRepoAdapter;
import com.techeazyhq.cm.bil.infra.R2dbcSubPlnRepoAdapter;
import com.techeazyhq.cm.bil.infra.RedisUsgCtrAdapter;

@Configuration
public class BilBeanConfig {

    @Bean
    public DfSubRepoPort dfSubRepoPort(DatabaseClient db) {
        return new R2dbcDfSubRepoAdapter(db);
    }

    @Bean
    public SubPlnRepoPort subPlnRepoPort(DatabaseClient db) {
        return new R2dbcSubPlnRepoAdapter(db);
    }

    /** What billing does, through its outbox to the audit ledger (ADR-040). */
    @Bean
    public R2dbcBilOutbox bilOutbox(DatabaseClient db, ObjectMapper objectMapper, ReactivePulsarClient reactivePulsarClient) {
        return new R2dbcBilOutbox(db, objectMapper, reactivePulsarClient);
    }

    @Bean
    public AuditTrail auditTrail(R2dbcBilOutbox outbox) {
        return AuditTrail.to("bil", outbox::enqueue);
    }

    @Bean
    public PlnChgReqRepoPort plnChgReqRepoPort(DatabaseClient db) {
        return new R2dbcPlnChgReqRepoAdapter(db);
    }

    @Bean
    public PlnChgReqHandler plnChgReqHandler(PlnChgReqRepoPort reqs, DfSubRepoPort subs, SubPlnRepoPort plans,
                                             AuditTrail audit) {
        return new PlnChgReqHandler(reqs, subs, plans, audit);
    }

    @Bean
    public BilOverviewQryHandler bilOverviewQryHandler(DfSubRepoPort subs, SubPlnRepoPort plans, UsgPort usage) {
        return new BilOverviewQryHandler(subs, plans, usage);
    }

    @Bean
    public PlnChgReqRepoPort plnChgReqRepoPort(DatabaseClient db) {
        return new R2dbcPlnChgReqRepoAdapter(db);
    }

    @Bean
    public PlnChgReqHandler plnChgReqHandler(PlnChgReqRepoPort reqs, DfSubRepoPort subs, SubPlnRepoPort plans) {
        return new PlnChgReqHandler(reqs, subs, plans);
    }

    @Bean
    public BilOverviewQryHandler bilOverviewQryHandler(DfSubRepoPort subs, SubPlnRepoPort plans, UsgPort usage) {
        return new BilOverviewQryHandler(subs, plans, usage);
    }

    @Bean
    public UsgPort usgPort(ReactiveStringRedisTemplate redisTemplate) {
        return new RedisUsgCtrAdapter(redisTemplate);
    }

    @Bean
    public OnDfActivatedEvtHandler onDfActivatedEvtHandler(DfSubRepoPort dfSubRepo, SubPlnRepoPort subPlnRepo,
                                                           AuditTrail audit) {
        return new OnDfActivatedEvtHandler(dfSubRepo, subPlnRepo, audit);
    }

    @Bean
    public AssignSubPlnCmdHandler assignSubPlnCmdHandler(DfSubRepoPort dfSubRepo, SubPlnRepoPort subPlnRepo,
                                                         AuditTrail audit) {
        return new AssignSubPlnCmdHandler(dfSubRepo, subPlnRepo, audit);
    }

    @Bean
    public SetDfSubStsCmdHandler setDfSubStsCmdHandler(DfSubRepoPort dfSubRepo, AuditTrail audit) {
        return new SetDfSubStsCmdHandler(dfSubRepo, audit);
    }

    @Bean
    public GetUsgQryHandler getUsgQryHandler(UsgPort usgPort) {
        return new GetUsgQryHandler(usgPort);
    }

    @Bean
    public UsgReservationHandler usgReservationHandler(DfSubRepoPort dfSubRepo, SubPlnRepoPort subPlnRepo, UsgPort usgPort) {
        return new UsgReservationHandler(dfSubRepo, subPlnRepo, usgPort);
    }

    @Bean
    public SubEligQryHandler subEligQryHandler(DfSubRepoPort dfSubRepo, SubPlnRepoPort subPlnRepo, UsgPort usgPort) {
        return new SubEligQryHandler(dfSubRepo, subPlnRepo, usgPort);
    }

    @Bean
    public ListSubPlnsQryHandler listSubPlnsQryHandler(SubPlnRepoPort subPlnRepo) {
        return new ListSubPlnsQryHandler(subPlnRepo);
    }

    @Bean
    public GetDfSubStatusQryHandler getDfSubStatusQryHandler(DfSubRepoPort dfSubRepo, SubPlnRepoPort subPlnRepo, UsgPort usgPort) {
        return new GetDfSubStatusQryHandler(dfSubRepo, subPlnRepo, usgPort);
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
    public PulsarDfActivatedConsumer pulsarDfActivatedConsumer(ReactivePulsarClient reactivePulsarClient,
                                                                 ObjectMapper objectMapper,
                                                                 OnDfActivatedEvtHandler handler) {
        return new PulsarDfActivatedConsumer(reactivePulsarClient, objectMapper, handler);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startConsumers(ApplicationReadyEvent event) {
        event.getApplicationContext().getBean(PulsarDfActivatedConsumer.class).start();
    }
}
