package com.techeazyhq.cm.bil.dom;

import reactor.core.publisher.Mono;

public interface DfSubRepoPort {

    Mono<DfSub> save(DfSub dfSub);

    Mono<DfSub> findByDfId(String dfId);
}
