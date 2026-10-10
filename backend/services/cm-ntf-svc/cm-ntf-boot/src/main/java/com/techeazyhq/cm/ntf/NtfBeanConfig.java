package com.techeazyhq.cm.ntf;

import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.reactive.client.adapter.AdaptedReactivePulsarClientFactory;
import org.apache.pulsar.reactive.client.api.ReactivePulsarClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeazyhq.cm.ntf.app.NotifyDfCmdHandler;
import com.techeazyhq.cm.ntf.app.NotifyDpCmdHandler;
import com.techeazyhq.cm.ntf.app.NtfDispatchService;
import com.techeazyhq.cm.ntf.app.OtpSendCmdHandler;
import com.techeazyhq.cm.ntf.dom.EmailSenderPort;
import com.techeazyhq.cm.ntf.dom.NtfChnl;
import com.techeazyhq.cm.ntf.dom.PushSenderPort;
import com.techeazyhq.cm.ntf.dom.SmsSenderPort;
import com.techeazyhq.cm.ntf.dom.WhatsAppSenderPort;
import com.techeazyhq.cm.ntf.infra.CatcherStore;
import com.techeazyhq.cm.ntf.infra.NotificationServiceClient;
import com.techeazyhq.cm.ntf.infra.PulsarNtfCmdConsumer;
import com.techeazyhq.cm.ntf.infra.PushCatcherAdapter;
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
                @Value("${cm.ntf.notification-service.base-url}") String baseUrl,
                @Value("${cm.ntf.notification-service.api-key}") String apiKey,
                @Value("${cm.ntf.notification-service.signing-secret:}") String signingSecret,
                @Value("${cm.ntf.notification-service.from-address:}") String fromAddress,
                @Value("${cm.ntf.notification-service.otp-validity-seconds:300}") int otpValiditySeconds,
                @Value("${cm.ntf.notification-service.timeout:5s}") Duration timeout,
                @Value("${cm.ntf.notification-service.max-retries:3}") int maxRetries,
                @Value("${cm.ntf.notification-service.retry-backoff:500ms}") Duration retryBackoff) {
            if (apiKey.isBlank()) {
                throw new IllegalStateException("cm.ntf.notification-service.api-key must be set when cm.ntf.provider=notification-service");
            }
            return new NotificationServiceClient(webClientBuilder, objectMapper, new NotificationServiceClient.Settings(
                    baseUrl, apiKey, signingSecret, fromAddress, otpValiditySeconds, timeout, maxRetries, retryBackoff));
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
    public NotifyDpCmdHandler notifyDpCmdHandler(NtfDispatchService ntfDispatchService) {
        return new NotifyDpCmdHandler(ntfDispatchService);
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
