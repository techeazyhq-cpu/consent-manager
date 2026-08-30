package com.techeazyhq.cm.dfreg.dom;

import com.techeazyhq.cm.domkit.DomainEvt;

import reactor.core.publisher.Mono;

public interface ObxPort {

    Mono<Void> enqueue(DomainEvt evt, String aggType, String aggId);
}
