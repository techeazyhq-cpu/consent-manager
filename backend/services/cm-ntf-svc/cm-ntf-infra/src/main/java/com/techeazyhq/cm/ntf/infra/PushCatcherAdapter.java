package com.techeazyhq.cm.ntf.infra;

import com.techeazyhq.cm.ntf.dom.NtfMsg;
import com.techeazyhq.cm.ntf.dom.PushSenderPort;
import reactor.core.publisher.Mono;

public class PushCatcherAdapter implements PushSenderPort {

    private final CatcherStore store;

    public PushCatcherAdapter(CatcherStore store) {
        this.store = store;
    }

    @Override
    public Mono<Void> send(NtfMsg msg) {
        return Mono.fromRunnable(() -> store.capture(msg));
    }
}
