package com.techeazyhq.cm.dsr.dom;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface DpErasurePorts {

    interface Repo {

        /** Inserts or updates. A second running erasure for the same principal is {@link ErasureRunningException}. */
        Mono<DpErasure> save(DpErasure erasure);

        Mono<DpErasure> findById(String id);

        /** The principal's latest erasure, if any. */
        Mono<DpErasure> findLatestByDpId(String dpId);

        /** Erasures still running, for the job that sends their step again. */
        Flux<DpErasure> findRunning(int limit);

        /** For staff: in {@code sts} (all when null), newest first. */
        Flux<DpErasure> findBySts(DpErasure.Sts sts, int limit);
    }

    /** Sends a remote step to the service that owns it. */
    interface Steps {

        Mono<Void> send(DpErasure erasure);
    }

    /** The principal's account in the identity provider. */
    interface Identity {

        /** Deletes it, ending their sessions; one already gone is done. */
        Mono<Void> delete(String dpId);
    }

    class ErasureRunningException extends RuntimeException {

        public ErasureRunningException() {
            super("Your account is already being closed");
        }
    }
}
