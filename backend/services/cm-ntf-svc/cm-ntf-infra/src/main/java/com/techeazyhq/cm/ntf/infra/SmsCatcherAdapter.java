package com.techeazyhq.cm.ntf.infra;

import com.techeazyhq.cm.ntf.dom.NtfMsg;
import com.techeazyhq.cm.ntf.dom.SmsSenderPort;

import reactor.core.publisher.Mono;

public class SmsCatcherAdapter implements SmsSenderPort {

    private final CatcherStore store;

    public SmsCatcherAdapter(CatcherStore store) {
        this.store = store;
    }

    @Override
    public Mono<Void> send(NtfMsg msg) {
        return Mono.fromRunnable(() -> store.capture(msg));
    }
}
