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
