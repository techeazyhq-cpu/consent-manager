package com.techeazyhq.cm.dfreg.dom;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface DfRepoPort {

    Mono<Df> save(Df df);

    Mono<Df> findById(String id);

    Flux<Df> findAll();
}
