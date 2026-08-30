package com.techeazyhq.cm.bil.dom;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface SubPlnRepoPort {

    Mono<SubPln> save(SubPln subPln);

    Mono<SubPln> findById(String id);

    Mono<SubPln> findByName(String name);

    Flux<SubPln> findAll();
}
