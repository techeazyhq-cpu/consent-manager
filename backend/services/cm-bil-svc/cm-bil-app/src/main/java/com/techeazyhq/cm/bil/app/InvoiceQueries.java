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

package com.techeazyhq.cm.bil.app;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;

import com.techeazyhq.cm.bil.dom.Invoice;
import com.techeazyhq.cm.bil.dom.InvoiceRepoPort;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Reading invoices (ADR-079): a fiduciary reads its own, platform staff everyone's, and the accounting export. */
public class InvoiceQueries {

    private final InvoiceRepoPort repo;

    public InvoiceQueries(InvoiceRepoPort repo) {
        this.repo = repo;
    }

    /** A fiduciary's own invoices, newest first. */
    public Flux<Invoice> mine(String dfId) {
        return repo.list(dfId, null);
    }

    /** One of a fiduciary's own invoices; another fiduciary's is not found. */
    public Mono<Invoice> mine(String dfId, String id) {
        return repo.find(id).filter(i -> i.dfId().equals(dfId))
                .switchIfEmpty(Mono.error(() -> new DomRuleViolation(404, "invoice-not-found", "There is no such invoice")));
    }

    /** Everyone's, newest first, for one fiduciary and/or one month. */
    public Flux<Invoice> all(String dfId, YearMonth month) {
        Instant from = month == null ? null : month.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        return repo.list(dfId, from);
    }

    public Flux<InvoiceRepoPort.PlanPrice> prices() {
        return repo.prices();
    }
}
