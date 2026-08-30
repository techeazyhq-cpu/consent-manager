package com.techeazyhq.cm.dfreg;

import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.reactive.client.adapter.AdaptedReactivePulsarClientFactory;
import org.apache.pulsar.reactive.client.api.ReactivePulsarClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeazyhq.cm.dfreg.app.ActivateDfCmdHandler;
import com.techeazyhq.cm.dfreg.app.GetDfActiveQryHandler;
import com.techeazyhq.cm.dfreg.app.GetDfQryHandler;
import com.techeazyhq.cm.dfreg.app.ListDfsQryHandler;
import com.techeazyhq.cm.dfreg.app.RegisterDfCmdHandler;
import com.techeazyhq.cm.dfreg.dom.DfApiClientProvisionPort;
import com.techeazyhq.cm.dfreg.dom.DfRepoPort;
import com.techeazyhq.cm.dfreg.dom.ObxPort;
import com.techeazyhq.cm.dfreg.infra.KeycloakAdminAuthFilter;
import com.techeazyhq.cm.dfreg.infra.KeycloakDfApiClientProvisionAdapter;
import com.techeazyhq.cm.dfreg.infra.ObxRelay;
import com.techeazyhq.cm.dfreg.infra.PulsarDfEvtProducer;
import com.techeazyhq.cm.dfreg.infra.R2dbcDfRepoAdapter;
import com.techeazyhq.cm.dfreg.infra.R2dbcObxAdapter;

@Configuration
@EnableConfigurationProperties(DfRegBeanConfig.KeycloakProps.class)
public class DfRegBeanConfig {

    @Bean
    public DfRepoPort dfRepoPort(DatabaseClient db, ObjectMapper objectMapper) {
        return new R2dbcDfRepoAdapter(db, objectMapper);
    }

    @Bean
    public ObxPort obxPort(DatabaseClient db, ObjectMapper objectMapper) {
        return new R2dbcObxAdapter(db, objectMapper);
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
    public DfApiClientProvisionPort dfApiClientProvisionPort(WebClient keycloakAdminWebClient, KeycloakProps props) {
        return new KeycloakDfApiClientProvisionAdapter(keycloakAdminWebClient, props.realm());
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
    public PulsarDfEvtProducer pulsarDfEvtProducer(ReactivePulsarClient reactivePulsarClient) {
        return new PulsarDfEvtProducer(reactivePulsarClient);
    }

    @Bean
    public RegisterDfCmdHandler registerDfCmdHandler(DfRepoPort dfRepo) {
        return new RegisterDfCmdHandler(dfRepo);
    }

    @Bean
    public ActivateDfCmdHandler activateDfCmdHandler(DfRepoPort dfRepo, DfApiClientProvisionPort provisionPort) {
        return new ActivateDfCmdHandler(dfRepo, provisionPort);
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

    @ConfigurationProperties(prefix = "cm.keycloak")
    public record KeycloakProps(String baseUrl, String realm, String adminUsername, String adminPassword) {
    }
}
