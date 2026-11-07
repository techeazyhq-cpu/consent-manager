/*
 * Copyright 2026 Vasantha Kumar
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * @author Vasantha Kumar <vasantha.kumar@hotmail.com>
 */

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
