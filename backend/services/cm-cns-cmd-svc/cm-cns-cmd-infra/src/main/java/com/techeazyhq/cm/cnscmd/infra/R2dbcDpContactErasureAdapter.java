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

package com.techeazyhq.cm.cnscmd.infra;

import java.util.List;

import org.springframework.r2dbc.core.DatabaseClient;

import com.techeazyhq.cm.cnscmd.dom.DpContactErasurePort;

import reactor.core.publisher.Mono;

/**
 * {@link DpContactErasurePort} in SQL. A list row keeps its outcome and the fiduciary's own reference, with the contact
 * replaced by {@code [erased]}; an event keeps everything but {@code dpRef.val}. Only published events are changed:
 * one not yet relayed still needs its contact, and is erased when the step runs again.
 */
public class R2dbcDpContactErasureAdapter implements DpContactErasurePort {

    static final String ERASED = "[erased]";

    private final DatabaseClient db;

    public R2dbcDpContactErasureAdapter(DatabaseClient db) {
        this.db = db;
    }

    @Override
    public Mono<Long> eraseContacts(List<String> cnsReqIds, List<String> cnsArtIds) {
        if (cnsReqIds.isEmpty() && cnsArtIds.isEmpty()) {
            return Mono.just(0L);
        }
        String[] reqs = cnsReqIds.toArray(String[]::new);
        String[] aggs = java.util.stream.Stream.concat(cnsReqIds.stream(), cnsArtIds.stream()).toArray(String[]::new);
        Mono<Long> rows = db.sql("""
                        update cns_cmd.cns_req_batch_row set contact = :erased, val = null
                        where cns_req_id = any(:ids) and contact <> :erased
                        """)
                .bind("erased", ERASED)
                .bind("ids", reqs)
                .fetch().rowsUpdated();
        Mono<Long> events = db.sql("""
                        update cns_cmd.outbox_evt
                        set payload_json = jsonb_set(payload_json::jsonb, '{dpRef,val}', to_jsonb(:erased::text))::text
                        where agg_id = any(:ids) and published_at is not null
                          and payload_json::jsonb #>> '{dpRef,val}' is not null
                          and payload_json::jsonb #>> '{dpRef,val}' <> :erased
                        """)
                .bind("erased", ERASED)
                .bind("ids", aggs)
                .fetch().rowsUpdated();
        return rows.flatMap(r -> events.map(e -> r + e));
    }

    @Override
    public Mono<Long> eraseContactsOfDf(String dfId) {
        Mono<Long> rows = db.sql("""
                        update cns_cmd.cns_req_batch_row set contact = :erased, val = null
                        where cns_req_id in (select id from cns_cmd.cns_req where df_id = :dfId) and contact <> :erased
                        """)
                .bind("erased", ERASED).bind("dfId", dfId)
                .fetch().rowsUpdated();
        Mono<Long> events = db.sql("""
                        update cns_cmd.outbox_evt
                        set payload_json = jsonb_set(payload_json::jsonb, '{dpRef,val}', to_jsonb(:erased::text))::text
                        where (agg_id in (select id from cns_cmd.cns_req where df_id = :dfId)
                               or agg_id in (select id from cns_cmd.cns_art where df_id = :dfId))
                          and published_at is not null
                          and payload_json::jsonb #>> '{dpRef,val}' is not null
                          and payload_json::jsonb #>> '{dpRef,val}' <> :erased
                        """)
                .bind("erased", ERASED).bind("dfId", dfId)
                .fetch().rowsUpdated();
        return rows.flatMap(r -> events.map(e -> r + e));
    }
}
