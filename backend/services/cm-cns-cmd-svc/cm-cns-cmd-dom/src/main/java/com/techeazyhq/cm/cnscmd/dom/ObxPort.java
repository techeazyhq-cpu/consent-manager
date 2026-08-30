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
