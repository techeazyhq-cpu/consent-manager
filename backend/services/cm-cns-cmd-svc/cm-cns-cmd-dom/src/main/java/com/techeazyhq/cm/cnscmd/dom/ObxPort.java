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

package com.techeazyhq.cm.cnscmd.dom;

import com.techeazyhq.cm.domkit.DomainEvt;

import reactor.core.publisher.Mono;

/**
 * Outbox port (plan §12): enqueues a domain event for reliable publishing. An aggregate's
 * own {@code pullEvts()} events are enqueued automatically by the repo adapter that saves
 * it (same transaction); this port exists for events a command handler composes itself from
 * more than one aggregate's outcome — e.g. {@code CnsGrantedEvt}, which needs the signed
 * {@code CnsTkn} that only exists after cm-sec-kit signs it (plan §7c step 3).
 */
public interface ObxPort {

    Mono<Void> enqueue(DomainEvt evt, String aggType, String aggId);
}
