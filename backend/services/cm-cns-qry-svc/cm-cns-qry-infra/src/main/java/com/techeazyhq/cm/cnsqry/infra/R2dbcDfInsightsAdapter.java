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

package com.techeazyhq.cm.cnsqry.infra;

import java.time.Instant;
import java.time.LocalDate;

import org.springframework.r2dbc.core.DatabaseClient;

import com.techeazyhq.cm.cnsqry.dom.DfInsights;
import com.techeazyhq.cm.cnsqry.dom.DfInsightsPort;

import io.r2dbc.spi.Readable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * {@link DfInsightsPort} over the read model (ADR-049). A request's decision is its first move to ISSUED or DENIED in
 * the history; later changes of a given consent are ISSUED to ISSUED and don't count as deciding again.
 */
public class R2dbcDfInsightsAdapter implements DfInsightsPort {

    /** The fiduciary's requests raised in the window, each with its first decision, if any. */
    private static final String REQS = """
            with req as (
                select r.cns_req_id, r.prps_code, r.lcy_sts, r.created_at, d.to_sts as decision, d.occurred_at as decided_at
                from cns_qry.cns_art_rd_mdl r
                left join lateral (
                    select h.to_sts, h.occurred_at from cns_qry.cns_hst_rd_mdl h
                    where h.cns_req_id = r.cns_req_id and h.to_sts in ('ISSUED', 'DENIED')
                    order by h.occurred_at limit 1) d on true
                where r.df_id = :dfId and r.created_at >= :from and r.created_at < :to)
            """;

    private final DatabaseClient db;

    public R2dbcDfInsightsAdapter(DatabaseClient db) {
        this.db = db;
    }

    @Override
    public Flux<DfInsights.Purpose> byPurpose(String dfId, Instant from, Instant to) {
        return db.sql(REQS + """
                        select prps_code,
                               count(*) as raised,
                               count(*) filter (where decision = 'ISSUED') as given,
                               count(*) filter (where decision = 'DENIED') as refused,
                               count(*) filter (where decision is null and lcy_sts = 'PENDING') as awaiting,
                               count(*) filter (where decision is null and lcy_sts = 'NOTIFICATION_FAILED') as not_notified,
                               percentile_cont(0.5) within group (order by extract(epoch from decided_at - created_at))
                                   filter (where decision is not null) as median_s
                        from req group by prps_code
                        """)
                .bind("dfId", dfId).bind("from", from).bind("to", to)
                .map((row, meta) -> new DfInsights.Purpose(row.get("prps_code", String.class),
                        new DfInsights.Outcomes(n(row, "raised"), n(row, "given"), n(row, "refused"),
                                n(row, "awaiting"), n(row, "not_notified")),
                        seconds(row, "median_s")))
                .all();
    }

    @Override
    public Mono<DfInsights.Decisions> decisions(String dfId, Instant from, Instant to) {
        return db.sql(REQS + """
                        select count(*) as decided,
                               percentile_cont(0.5) within group (order by extract(epoch from decided_at - created_at)) as median_s,
                               percentile_cont(0.9) within group (order by extract(epoch from decided_at - created_at)) as p90_s,
                               count(*) filter (where decided_at - created_at <= interval '1 day') as within_a_day
                        from req where decision is not null
                        """)
                .bind("dfId", dfId).bind("from", from).bind("to", to)
                .map((row, meta) -> new DfInsights.Decisions(n(row, "decided"), seconds(row, "median_s"),
                        seconds(row, "p90_s"), n(row, "within_a_day")))
                .one();
    }

    @Override
    public Flux<DfInsights.Day> byDay(String dfId, Instant from, Instant to) {
        return db.sql(REQS + """
                        , raised as (
                            select (created_at at time zone 'UTC')::date as day, count(*) as n from req group by 1),
                          decided as (
                            select (decided_at at time zone 'UTC')::date as day,
                                   count(*) filter (where decision = 'ISSUED') as given,
                                   count(*) filter (where decision = 'DENIED') as refused
                            from req where decision is not null group by 1)
                        select coalesce(r.day, d.day) as day, coalesce(r.n, 0) as raised,
                               coalesce(d.given, 0) as given, coalesce(d.refused, 0) as refused
                        from raised r full join decided d on r.day = d.day
                        order by 1
                        """)
                .bind("dfId", dfId).bind("from", from).bind("to", to)
                .map((row, meta) -> new DfInsights.Day(row.get("day", LocalDate.class), n(row, "raised"),
                        n(row, "given"), n(row, "refused")))
                .all();
    }

    @Override
    public Mono<DfInsights.Changes> changes(String dfId, Instant from, Instant to) {
        return db.sql("""
                        select count(*) filter (where h.to_sts = 'REVOKED') as withdrawn,
                               count(*) filter (where h.from_sts = 'ISSUED' and h.to_sts = 'ISSUED'
                                                and h.chg is not null and h.chg <> 'RENEWED') as narrowed,
                               count(*) filter (where h.chg = 'RENEWED') as renewed,
                               count(*) filter (where h.to_sts = 'EXPIRED') as lapsed
                        from cns_qry.cns_hst_rd_mdl h
                        join cns_qry.cns_art_rd_mdl r on r.cns_req_id = h.cns_req_id
                        where r.df_id = :dfId and h.occurred_at >= :from and h.occurred_at < :to
                        """)
                .bind("dfId", dfId).bind("from", from).bind("to", to)
                .map((row, meta) -> new DfInsights.Changes(n(row, "withdrawn"), n(row, "narrowed"), n(row, "renewed"),
                        n(row, "lapsed")))
                .one();
    }

    private static long n(Readable row, String column) {
        Number v = row.get(column, Number.class);
        return v == null ? 0 : v.longValue();
    }

    private static Long seconds(Readable row, String column) {
        Number v = row.get(column, Number.class);
        return v == null ? null : Math.round(v.doubleValue());
    }
}
