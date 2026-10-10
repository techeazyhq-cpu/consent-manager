package com.techeazyhq.cm.ntf.dom;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Port: a DP's registered push devices. Implemented by {@code R2dbcPushDvcRepoAdapter}. */
public interface PushDvcRepoPort {

    /**
     * Registers {@code token} for {@code dpId}, or refreshes it if already registered — moving it to {@code dpId} if
     * another DP had it — then drops that DP's oldest devices beyond {@link PushDvc#MAX_PER_DP}.
     */
    Mono<PushDvc> upsert(String dpId, String token, PushPltf platform);

    Flux<PushDvc> findByDpId(String dpId);

    /** @return whether a device {@code id} belonging to {@code dpId} existed */
    Mono<Boolean> delete(String dpId, String id);
}
