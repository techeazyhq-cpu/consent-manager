package com.techeazyhq.cm.ntf.infra;

import com.techeazyhq.cm.ntf.dom.NtfMsg;
import com.techeazyhq.cm.ntf.dom.WhatsAppSenderPort;

import reactor.core.publisher.Mono;

public class WhatsAppCatcherAdapter implements WhatsAppSenderPort {

    private final CatcherStore store;

    public WhatsAppCatcherAdapter(CatcherStore store) {
        this.store = store;
    }

    @Override
    public Mono<Void> send(NtfMsg msg) {
        return Mono.fromRunnable(() -> store.capture(msg));
    }
}
