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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.r2dbc.core.DatabaseClient;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeazyhq.cm.cnscmd.dom.WhkDelivery;
import com.techeazyhq.cm.cnscmd.dom.WhkEndpoint;
import com.techeazyhq.cm.cnscmd.dom.WhkMessage;
import com.techeazyhq.cm.cnscmd.dom.WhkRepoPort;

import io.r2dbc.spi.Readable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** {@link WhkRepoPort} adapter (ADR-047). */
public class R2dbcWhkRepoAdapter implements WhkRepoPort {

    private final DatabaseClient db;
    private final ObjectMapper objectMapper;

    public R2dbcWhkRepoAdapter(DatabaseClient db, ObjectMapper objectMapper) {
        this.db = db;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<WhkEndpoint> saveEndpoint(WhkEndpoint e) {
        DatabaseClient.GenericExecuteSpec spec = db.sql("""
                        insert into cns_cmd.whk_endpoint (id, df_id, url, description, types, active, secret_sealed,
                                                          prev_secret_sealed, prev_secret_until, created_at)
                        values (:id, :dfId, :url, :description, cast(:types as jsonb), :active, :secret, :prevSecret,
                                :prevUntil, :createdAt)
                        on conflict (id) do update set
                            active = excluded.active,
                            secret_sealed = excluded.secret_sealed,
                            prev_secret_sealed = excluded.prev_secret_sealed,
                            prev_secret_until = excluded.prev_secret_until
                        """)
                .bind("id", e.id())
                .bind("dfId", e.dfId())
                .bind("url", e.url())
                .bind("types", json(e.types()))
                .bind("active", e.active())
                .bind("secret", e.secretSealed())
                .bind("createdAt", e.createdAt());
        spec = R2dbcBinds.bindNullable(spec, "description", e.description(), String.class);
        spec = R2dbcBinds.bindNullable(spec, "prevSecret", e.prevSecretSealed(), String.class);
        spec = R2dbcBinds.bindNullable(spec, "prevUntil", e.prevSecretUntil(), Instant.class);
        return spec.then().thenReturn(e);
    }

    @Override
    public Mono<WhkEndpoint> findEndpoint(String dfId, String id) {
        return db.sql("select * from cns_cmd.whk_endpoint where df_id = :dfId and id = :id")
                .bind("dfId", dfId).bind("id", id)
                .map((row, meta) -> endpoint(row)).one();
    }

    @Override
    public Flux<WhkEndpoint> endpoints(String dfId) {
        return db.sql("select * from cns_cmd.whk_endpoint where df_id = :dfId order by created_at")
                .bind("dfId", dfId)
                .map((row, meta) -> endpoint(row)).all();
    }

    @Override
    public Mono<Void> deleteEndpoint(String dfId, String id) {
        return db.sql("delete from cns_cmd.whk_endpoint where df_id = :dfId and id = :id")
                .bind("dfId", dfId).bind("id", id).then();
    }

    @Override
    public Mono<Void> enqueue(WhkMessage msg) {
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(msg.body()))
                .flatMap(payload -> db.sql("""
                                insert into cns_cmd.whk_delivery (id, endpoint_id, df_id, msg_id, type, payload, sts, attempts,
                                                                  next_attempt_at, created_at)
                                select gen_random_uuid(), e.id, e.df_id, :msgId, :type, :payload, 'PENDING', 0, :now, :now
                                from cns_cmd.whk_endpoint e
                                where e.df_id = :dfId and e.active
                                  and (jsonb_array_length(e.types) = 0 or jsonb_exists(e.types, :type))
                                on conflict (endpoint_id, msg_id) do nothing
                                """)
                        .bind("msgId", msg.id())
                        .bind("type", msg.type())
                        .bind("payload", payload)
                        .bind("dfId", msg.dfId())
                        .bind("now", Instant.now())
                        .then());
    }

    @Override
    public Mono<WhkDelivery> enqueueFor(WhkEndpoint endpoint, WhkMessage msg) {
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(msg.body()))
                .map(payload -> new WhkDelivery(UUID.randomUUID().toString(), endpoint.id(), endpoint.dfId(), msg.id(),
                        msg.type(), payload, WhkDelivery.Sts.PENDING, 0, Instant.now(), null, null, null, Instant.now()))
                .flatMap(this::saveDelivery);
    }

    @Override
    public Flux<WhkDelivery> claimDue(Instant now, Duration lease, int limit) {
        return db.sql("""
                        update cns_cmd.whk_delivery d set next_attempt_at = :leaseUntil
                        where d.id in (select id from cns_cmd.whk_delivery
                                       where sts = 'PENDING' and next_attempt_at <= :now
                                       order by next_attempt_at
                                       limit :limit
                                       for update skip locked)
                        returning d.*
                        """)
                .bind("leaseUntil", now.plus(lease))
                .bind("now", now)
                .bind("limit", limit)
                .map((row, meta) -> delivery(row)).all();
    }

    @Override
    public Mono<Void> saveAttempt(WhkDelivery after, WhkDelivery.Attempt a) {
        DatabaseClient.GenericExecuteSpec attempt = db.sql("""
                        insert into cns_cmd.whk_attempt (delivery_id, n, at, status, error, millis)
                        values (:id, :n, :at, :status, :error, :millis)
                        on conflict (delivery_id, n) do nothing
                        """)
                .bind("id", UUID.fromString(after.id()))
                .bind("n", a.n())
                .bind("at", a.at())
                .bind("millis", a.millis());
        attempt = R2dbcBinds.bindNullable(attempt, "status", a.status(), Integer.class);
        attempt = R2dbcBinds.bindNullable(attempt, "error", clip(a.error()), String.class);
        return attempt.then().then(saveDelivery(after)).then();
    }

    @Override
    public Mono<WhkDelivery> saveDelivery(WhkDelivery d) {
        DatabaseClient.GenericExecuteSpec spec = db.sql("""
                        insert into cns_cmd.whk_delivery (id, endpoint_id, df_id, msg_id, type, payload, sts, attempts,
                                                          next_attempt_at, last_status, last_error, delivered_at, created_at)
                        values (:id, :endpointId, :dfId, :msgId, :type, :payload, :sts, :attempts, :next, :lastStatus,
                                :lastError, :deliveredAt, :createdAt)
                        on conflict (id) do update set
                            sts = excluded.sts,
                            attempts = excluded.attempts,
                            next_attempt_at = excluded.next_attempt_at,
                            last_status = excluded.last_status,
                            last_error = excluded.last_error,
                            delivered_at = excluded.delivered_at
                        """)
                .bind("id", UUID.fromString(d.id()))
                .bind("endpointId", d.endpointId())
                .bind("dfId", d.dfId())
                .bind("msgId", d.msgId())
                .bind("type", d.type())
                .bind("payload", d.payload())
                .bind("sts", d.sts().name())
                .bind("attempts", d.attempts())
                .bind("createdAt", d.createdAt());
        spec = R2dbcBinds.bindNullable(spec, "next", d.nextAttemptAt(), Instant.class);
        spec = R2dbcBinds.bindNullable(spec, "lastStatus", d.lastStatus(), Integer.class);
        spec = R2dbcBinds.bindNullable(spec, "lastError", clip(d.lastError()), String.class);
        spec = R2dbcBinds.bindNullable(spec, "deliveredAt", d.deliveredAt(), Instant.class);
        return spec.then().thenReturn(d);
    }

    @Override
    public Mono<WhkDelivery> findDelivery(String dfId, String id) {
        UUID uuid;
        try {
            uuid = UUID.fromString(id);
        } catch (IllegalArgumentException notAnId) {
            return Mono.empty();
        }
        return db.sql("select * from cns_cmd.whk_delivery where df_id = :dfId and id = :id")
                .bind("dfId", dfId).bind("id", uuid)
                .map((row, meta) -> delivery(row)).one();
    }

    @Override
    public Flux<WhkDelivery> deliveries(String dfId, String endpointId, WhkDelivery.Sts sts, int limit) {
        DatabaseClient.GenericExecuteSpec spec = db.sql("""
                        select * from cns_cmd.whk_delivery
                        where df_id = :dfId
                          and (cast(:endpointId as varchar) is null or endpoint_id = :endpointId)
                          and (cast(:sts as varchar) is null or sts = :sts)
                        order by created_at desc
                        limit :limit
                        """)
                .bind("dfId", dfId)
                .bind("limit", limit);
        spec = R2dbcBinds.bindNullable(spec, "endpointId", endpointId, String.class);
        spec = R2dbcBinds.bindNullable(spec, "sts", sts == null ? null : sts.name(), String.class);
        return spec.map((row, meta) -> delivery(row)).all();
    }

    @Override
    public Flux<WhkDelivery.Attempt> attempts(String deliveryId) {
        return db.sql("select * from cns_cmd.whk_attempt where delivery_id = :id order by n")
                .bind("id", UUID.fromString(deliveryId))
                .map((row, meta) -> new WhkDelivery.Attempt(row.get("n", Integer.class), row.get("at", Instant.class),
                        row.get("status", Integer.class), row.get("error", String.class), row.get("millis", Long.class)))
                .all();
    }

    private WhkEndpoint endpoint(Readable row) {
        return new WhkEndpoint(row.get("id", String.class), row.get("df_id", String.class), row.get("url", String.class),
                row.get("description", String.class), types(row.get("types", String.class)),
                Boolean.TRUE.equals(row.get("active", Boolean.class)), row.get("secret_sealed", String.class),
                row.get("prev_secret_sealed", String.class), row.get("prev_secret_until", Instant.class),
                row.get("created_at", Instant.class));
    }

    private static WhkDelivery delivery(Readable row) {
        return new WhkDelivery(row.get("id", UUID.class).toString(), row.get("endpoint_id", String.class),
                row.get("df_id", String.class), row.get("msg_id", String.class), row.get("type", String.class),
                row.get("payload", String.class), WhkDelivery.Sts.valueOf(row.get("sts", String.class)),
                row.get("attempts", Integer.class), row.get("next_attempt_at", Instant.class),
                row.get("last_status", Integer.class), row.get("last_error", String.class),
                row.get("delivered_at", Instant.class), row.get("created_at", Instant.class));
    }

    private String json(Set<String> types) {
        try {
            return objectMapper.writeValueAsString(types.stream().sorted().toList());
        } catch (Exception e) {
            throw new IllegalStateException("Cannot write webhook types", e);
        }
    }

    private Set<String> types(String json) {
        try {
            return json == null ? Set.of() : Set.copyOf(objectMapper.readValue(json, new TypeReference<List<String>>() {
            }));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot read webhook types", e);
        }
    }

    private static String clip(String s) {
        return s == null || s.length() <= 300 ? s : s.substring(0, 300);
    }
}
