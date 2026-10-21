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

package com.techeazyhq.cm.ntf;

import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.reactive.client.adapter.AdaptedReactivePulsarClientFactory;
import org.apache.pulsar.reactive.client.api.ReactivePulsarClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeazyhq.cm.ntf.app.NotifyDfCmdHandler;
import com.techeazyhq.cm.ntf.app.NotifyDpCmdHandler;
import com.techeazyhq.cm.ntf.app.NtfDispatchService;
import com.techeazyhq.cm.ntf.app.OtpSendCmdHandler;
import com.techeazyhq.cm.ntf.app.PushDvcCmdHandler;
import com.techeazyhq.cm.ntf.dom.DpDeliveryOutcomePort;
import com.techeazyhq.cm.ntf.dom.EmailSenderPort;
import com.techeazyhq.cm.ntf.dom.NtfChnl;
import com.techeazyhq.cm.ntf.dom.PushDvcRepoPort;
import com.techeazyhq.cm.ntf.dom.PushSenderPort;
import com.techeazyhq.cm.ntf.dom.SmsSenderPort;
import com.techeazyhq.cm.ntf.dom.WhatsAppSenderPort;
import com.techeazyhq.cm.ntf.infra.CatcherStore;
import com.techeazyhq.cm.ntf.infra.NotificationServiceClient;
import com.techeazyhq.cm.ntf.infra.PulsarDpDeliveryOutcomeAdapter;
import com.techeazyhq.cm.ntf.infra.PulsarNtfCmdConsumer;
import com.techeazyhq.cm.ntf.infra.PushCatcherAdapter;
import com.techeazyhq.cm.ntf.infra.R2dbcPushDvcRepoAdapter;
import com.techeazyhq.cm.ntf.infra.SmsCatcherAdapter;
import com.techeazyhq.cm.ntf.infra.SmtpEmailSenderAdapter;
import com.techeazyhq.cm.ntf.infra.WhatsAppCatcherAdapter;

import java.time.Duration;

/** Composition root (plan §5) for the Notification service. */
@Configuration
public class NtfBeanConfig {

    @Bean("smsCatcherStore")
    public CatcherStore smsCatcherStore() {
        return new CatcherStore();
    }

    @Bean("whatsAppCatcherStore")
    public CatcherStore whatsAppCatcherStore() {
        return new CatcherStore();
    }

    @Bean("pushCatcherStore")
    public CatcherStore pushCatcherStore() {
        return new CatcherStore();
    }

    /** {@code cm.ntf.provider=local} (default): Mailpit for email, the in-memory catchers for everything else. */
    @Configuration
    @ConditionalOnProperty(name = "cm.ntf.provider", havingValue = "local", matchIfMissing = true)
    static class LocalChannels {

        @Bean
        EmailSenderPort emailSenderPort(JavaMailSender mailSender, @Value("${cm.ntf.from-address}") String fromAddress) {
            return new SmtpEmailSenderAdapter(mailSender, fromAddress);
        }

        @Bean
        SmsSenderPort smsSenderPort(@Qualifier("smsCatcherStore") CatcherStore smsCatcherStore) {
            return new SmsCatcherAdapter(smsCatcherStore);
        }

        @Bean
        WhatsAppSenderPort whatsAppSenderPort(@Qualifier("whatsAppCatcherStore") CatcherStore whatsAppCatcherStore) {
            return new WhatsAppCatcherAdapter(whatsAppCatcherStore);
        }

        @Bean
        PushSenderPort pushSenderPort(@Qualifier("pushCatcherStore") CatcherStore pushCatcherStore) {
            return new PushCatcherAdapter(pushCatcherStore);
        }
    }

    /** {@code cm.ntf.provider=notification-service}: every channel goes through TechEazyHQ's notification-service. */
    @Configuration
    @ConditionalOnProperty(name = "cm.ntf.provider", havingValue = "notification-service")
    static class NotificationServiceChannels {

        @Bean
        NotificationServiceClient notificationServiceClient(
                WebClient.Builder webClientBuilder, ObjectMapper objectMapper,
                ReactiveCircuitBreakerFactory<?, ?> cbFactory,
                @Value("${cm.ntf.notification-service.base-url}") String baseUrl,
                @Value("${cm.ntf.notification-service.api-key}") String apiKey,
                @Value("${cm.ntf.notification-service.signing-secret:}") String signingSecret,
                @Value("${cm.ntf.notification-service.from-address:}") String fromAddress,
                @Value("${cm.ntf.notification-service.otp-validity-seconds:300}") int otpValiditySeconds,
                @Value("${cm.ntf.notification-service.timeout:5s}") Duration timeout,
                @Value("${cm.ntf.notification-service.max-retries:3}") int maxRetries,
                @Value("${cm.ntf.notification-service.retry-backoff:500ms}") Duration retryBackoff,
                @Value("${cm.ntf.notification-service.push-data:true}") boolean pushData,
                @Value("${cm.sandbox.enabled:false}") boolean sandbox) {
            // The sandbox never delivers (ADR-034, ADR-037): its messages stay in its own inbox, Mailpit and the catchers.
            if (sandbox) {
                throw new IllegalStateException("cm.ntf.provider=notification-service would deliver for real; "
                        + "the sandbox (cm.sandbox.enabled=true) runs only with cm.ntf.provider=local");
            }
            if (apiKey.isBlank()) {
                throw new IllegalStateException("cm.ntf.notification-service.api-key must be set when cm.ntf.provider=notification-service");
            }
            return new NotificationServiceClient(webClientBuilder, objectMapper, cbFactory, new NotificationServiceClient.Settings(
                    baseUrl, apiKey, signingSecret, fromAddress, otpValiditySeconds, timeout, maxRetries, retryBackoff,
                    pushData));
        }

        @Bean
        EmailSenderPort emailSenderPort(NotificationServiceClient client) {
            return msg -> client.send(NtfChnl.EMAIL, msg);
        }

        @Bean
        SmsSenderPort smsSenderPort(NotificationServiceClient client) {
            return msg -> client.send(NtfChnl.SMS, msg);
        }

        @Bean
        WhatsAppSenderPort whatsAppSenderPort(NotificationServiceClient client) {
            return msg -> client.send(NtfChnl.WHATSAPP, msg);
        }

        @Bean
        PushSenderPort pushSenderPort(NotificationServiceClient client) {
            return msg -> client.send(NtfChnl.PUSH, msg);
        }
    }

    @Bean
    public NtfDispatchService ntfDispatchService(EmailSenderPort emailSenderPort, SmsSenderPort smsSenderPort,
                                                  WhatsAppSenderPort whatsAppSenderPort, PushSenderPort pushSenderPort) {
        return new NtfDispatchService(emailSenderPort, smsSenderPort, whatsAppSenderPort, pushSenderPort);
    }

    @Bean
    public OtpSendCmdHandler otpSendCmdHandler(NtfDispatchService ntfDispatchService) {
        return new OtpSendCmdHandler(ntfDispatchService);
    }

    @Bean
    public NotifyDpCmdHandler notifyDpCmdHandler(NtfDispatchService ntfDispatchService, PushDvcRepoPort pushDvcRepoPort,
                                                 DpDeliveryOutcomePort dpDeliveryOutcomePort) {
        return new NotifyDpCmdHandler(ntfDispatchService, pushDvcRepoPort, dpDeliveryOutcomePort);
    }

    @Bean
    public DpDeliveryOutcomePort dpDeliveryOutcomePort(ReactivePulsarClient reactivePulsarClient, ObjectMapper objectMapper) {
        return new PulsarDpDeliveryOutcomeAdapter(reactivePulsarClient, objectMapper);
    }

    @Bean
    public PushDvcRepoPort pushDvcRepoPort(DatabaseClient databaseClient, TransactionalOperator transactionalOperator) {
        return new R2dbcPushDvcRepoAdapter(databaseClient, transactionalOperator);
    }

    @Bean
    public PushDvcCmdHandler pushDvcCmdHandler(PushDvcRepoPort pushDvcRepoPort) {
        return new PushDvcCmdHandler(pushDvcRepoPort);
    }

    @Bean
    public NotifyDfCmdHandler notifyDfCmdHandler() {
        return new NotifyDfCmdHandler();
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
    public PulsarNtfCmdConsumer pulsarNtfCmdConsumer(ReactivePulsarClient reactivePulsarClient, ObjectMapper objectMapper,
                                                       NotifyDpCmdHandler notifyDpCmdHandler,
                                                       NotifyDfCmdHandler notifyDfCmdHandler) {
        return new PulsarNtfCmdConsumer(reactivePulsarClient, objectMapper, notifyDpCmdHandler, notifyDfCmdHandler);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startConsumers(ApplicationReadyEvent event) {
        event.getApplicationContext().getBean(PulsarNtfCmdConsumer.class).start();
    }
}
