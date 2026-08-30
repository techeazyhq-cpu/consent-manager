package com.techeazyhq.cm.dfreg.infra;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.reactive.client.adapter.AdaptedReactivePulsarClientFactory;
import org.apache.pulsar.reactive.client.api.MessageSpec;
import org.apache.pulsar.reactive.client.api.ReactiveMessageSender;
import org.apache.pulsar.reactive.client.api.ReactiveMessageSenderCache;
import org.apache.pulsar.reactive.client.api.ReactivePulsarClient;

import reactor.core.publisher.Mono;

/** See cm-cns-cmd-infra's PulsarCnsEvtProducer for why {@code .cache(...)} is required alongside {@code .maxInflight(...)}. */
public class PulsarDfEvtProducer {

    private final ReactivePulsarClient reactivePulsarClient;
    private final ReactiveMessageSenderCache senderCache = AdaptedReactivePulsarClientFactory.createCache();
    private final Map<String, ReactiveMessageSender<String>> senders = new ConcurrentHashMap<>();

    public PulsarDfEvtProducer(ReactivePulsarClient reactivePulsarClient) {
        this.reactivePulsarClient = reactivePulsarClient;
    }

    public Mono<Void> publish(String topic, String payloadJson) {
        return senderFor(topic).sendOne(MessageSpec.of(payloadJson)).then();
    }

    private ReactiveMessageSender<String> senderFor(String topic) {
        return senders.computeIfAbsent(topic, t -> reactivePulsarClient.messageSender(Schema.STRING).topic(t).cache(senderCache).maxInflight(100).build());
    }
}
