package com.techeazyhq.cm.ntf;

import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.reactive.client.adapter.AdaptedReactivePulsarClientFactory;
import org.apache.pulsar.reactive.client.api.ReactivePulsarClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.mail.javamail.JavaMailSender;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeazyhq.cm.ntf.app.NotifyDfCmdHandler;
import com.techeazyhq.cm.ntf.app.NotifyDpCmdHandler;
import com.techeazyhq.cm.ntf.app.NtfDispatchService;
import com.techeazyhq.cm.ntf.app.OtpSendCmdHandler;
import com.techeazyhq.cm.ntf.dom.EmailSenderPort;
import com.techeazyhq.cm.ntf.dom.SmsSenderPort;
import com.techeazyhq.cm.ntf.dom.WhatsAppSenderPort;
import com.techeazyhq.cm.ntf.infra.CatcherStore;
import com.techeazyhq.cm.ntf.infra.PulsarNtfCmdConsumer;
import com.techeazyhq.cm.ntf.infra.SmsCatcherAdapter;
import com.techeazyhq.cm.ntf.infra.SmtpEmailSenderAdapter;
import com.techeazyhq.cm.ntf.infra.WhatsAppCatcherAdapter;

/** Composition root (plan §5) for the Notification service. */
@Configuration
public class NtfBeanConfig {

    @Bean
    public EmailSenderPort emailSenderPort(JavaMailSender mailSender, @Value("${cm.ntf.from-address}") String fromAddress) {
        return new SmtpEmailSenderAdapter(mailSender, fromAddress);
    }

    @Bean("smsCatcherStore")
    public CatcherStore smsCatcherStore() {
        return new CatcherStore();
    }

    @Bean("whatsAppCatcherStore")
    public CatcherStore whatsAppCatcherStore() {
        return new CatcherStore();
    }

    @Bean
    public SmsSenderPort smsSenderPort(@Qualifier("smsCatcherStore") CatcherStore smsCatcherStore) {
        return new SmsCatcherAdapter(smsCatcherStore);
    }

    @Bean
    public WhatsAppSenderPort whatsAppSenderPort(@Qualifier("whatsAppCatcherStore") CatcherStore whatsAppCatcherStore) {
        return new WhatsAppCatcherAdapter(whatsAppCatcherStore);
    }

    @Bean
    public NtfDispatchService ntfDispatchService(EmailSenderPort emailSenderPort, SmsSenderPort smsSenderPort,
                                                  WhatsAppSenderPort whatsAppSenderPort) {
        return new NtfDispatchService(emailSenderPort, smsSenderPort, whatsAppSenderPort);
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
