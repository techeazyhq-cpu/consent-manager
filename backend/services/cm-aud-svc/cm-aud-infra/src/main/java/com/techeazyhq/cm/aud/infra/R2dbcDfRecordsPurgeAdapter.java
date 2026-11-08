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


package com.techeazyhq.cm.aud.infra;

import java.time.Instant;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.reactive.TransactionalOperator;

import com.techeazyhq.cm.aud.dom.DfRecordsPurgePort;

import reactor.core.publisher.Mono;

/**
 * {@link DfRecordsPurgePort} for the ledger: the payload and the principal link of a fiduciary's entries go, in batches of
 * the oldest. {@code aud.entry} is append-only and is never touched; each entry commits to its payload's hash, so the
 * chain and its checkpoints verify without the payloads (as for an erased principal, ADR-059).
 */
public class R2dbcDfRecordsPurgeAdapter implements DfRecordsPurgePort {

    private final DatabaseClient db;
    private final TransactionalOperator tx;

    public R2dbcDfRecordsPurgeAdapter(DatabaseClient db, TransactionalOperator tx) {
        this.db = db;
        this.tx = tx;
    }

    @Override
    public Mono<Purged> purge(String dfId, Instant before, int limit) {
        String batch = """
                (select e.seq from aud.entry e
                 where e.df_id = :dfId and e.occurred_at < :before
                   and (exists (select 1 from aud.entry_payload p where p.seq = e.seq)
                        or exists (select 1 from aud.entry_subject s where s.seq = e.seq))
                 order by e.seq limit :limit)
                """;
        return tx.transactional(db.sql("delete from aud.entry_subject where seq in " + batch)
                        .bind("dfId", dfId).bind("before", before).bind("limit", limit).fetch().rowsUpdated()
                        .then(db.sql("delete from aud.entry_payload where seq in " + batch)
                                .bind("dfId", dfId).bind("before", before).bind("limit", limit).fetch().rowsUpdated()))
                .flatMap(deleted -> db.sql("""
                                select count(*) as n from aud.entry_payload p join aud.entry e on e.seq = p.seq where e.df_id = :dfId
                                """)
                        .bind("dfId", dfId).map((row, meta) -> row.get("n", Long.class)).one()
                        .map(left -> new Purged(deleted, left)));
    }
}
