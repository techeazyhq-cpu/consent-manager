package com.techeazyhq.cm.domkit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Base class for an Aggregate Root. Collects {@link DomainEvt}s raised while mutating the
 * aggregate; the infrastructure layer drains them (via {@link #pullEvts()}) and writes them
 * to the Outbox in the same transaction as the aggregate's own persistence (Observer + Outbox
 * patterns, plan §12) — the domain layer itself never talks to Pulsar directly.
 *
 * @param <ID> the aggregate's identity type
 */
public abstract class Agg<ID> {

    private final ID id;
    private final List<DomainEvt> evts = new ArrayList<>();

    protected Agg(ID id) {
        this.id = id;
    }

    public ID id() {
        return id;
    }

    protected void raise(DomainEvt evt) {
        evts.add(evt);
    }

    public List<DomainEvt> pullEvts() {
        List<DomainEvt> pulled = List.copyOf(evts);
        evts.clear();
        return pulled;
    }

    public List<DomainEvt> peekEvts() {
        return Collections.unmodifiableList(evts);
    }
}
