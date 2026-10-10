package com.techeazyhq.cm.ntf.infra;

import com.techeazyhq.cm.ntf.dom.PushDvc;
import com.techeazyhq.cm.ntf.dom.PushDvcRepoPort;
import com.techeazyhq.cm.ntf.dom.PushPltf;
import io.r2dbc.spi.Row;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.reactive.TransactionalOperator;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.UUID;

public class R2dbcPushDvcRepoAdapter implements PushDvcRepoPort {

    private static final String COLUMNS = "id, dp_id, token, platform, registered_at, last_seen_at";

    private final DatabaseClient db;
    private final TransactionalOperator tx;

    public R2dbcPushDvcRepoAdapter(DatabaseClient db, TransactionalOperator tx) {
        this.db = db;
        this.tx = tx;
    }

    @Override
    public Mono<PushDvc> upsert(String dpId, String token, PushPltf platform) {
        // A token that moves to another DP gets a new id: the previous DP's browser may still hold the old id.
        Mono<PushDvc> upsert = db.sql("""
                        insert into ntf.push_dvc (id, dp_id, token, platform, registered_at, last_seen_at)
                        values (:id, :dpId, :token, :platform, now(), now())
                        on conflict (token) do update set
                            id = case when push_dvc.dp_id = excluded.dp_id then push_dvc.id else excluded.id end,
                            registered_at = case when push_dvc.dp_id = excluded.dp_id then push_dvc.registered_at else now() end,
                            dp_id = excluded.dp_id,
                            platform = excluded.platform,
                            last_seen_at = now()
                        """ + "returning " + COLUMNS)
                .bind("id", UUID.randomUUID())
                .bind("dpId", dpId)
                .bind("token", token)
                .bind("platform", platform.name())
                .map((row, meta) -> toPushDvc(row))
                .one();
        Mono<Long> prune = db.sql("""
                        delete from ntf.push_dvc where dp_id = :dpId and id not in (
                            select id from ntf.push_dvc where dp_id = :dpId order by last_seen_at desc limit :keep)""")
                .bind("dpId", dpId)
                .bind("keep", PushDvc.MAX_PER_DP)
                .fetch()
                .rowsUpdated();
        return upsert.flatMap(dvc -> prune.thenReturn(dvc)).as(tx::transactional);
    }

    @Override
    public Flux<PushDvc> findByDpId(String dpId) {
        return db.sql("select " + COLUMNS + " from ntf.push_dvc where dp_id = :dpId order by last_seen_at desc")
                .bind("dpId", dpId)
                .map((row, meta) -> toPushDvc(row))
                .all();
    }

    @Override
    public Mono<Boolean> delete(String dpId, String id) {
        UUID uuid;
        try {
            uuid = UUID.fromString(id);
        } catch (IllegalArgumentException notAnId) {
            return Mono.just(false);
        }
        return db.sql("delete from ntf.push_dvc where id = :id and dp_id = :dpId")
                .bind("id", uuid)
                .bind("dpId", dpId)
                .fetch()
                .rowsUpdated()
                .map(count -> count > 0);
    }

    private static PushDvc toPushDvc(Row row) {
        return new PushDvc(
                row.get("id", UUID.class).toString(),
                row.get("dp_id", String.class),
                row.get("token", String.class),
                PushPltf.valueOf(row.get("platform", String.class)),
                row.get("registered_at", OffsetDateTime.class).toInstant(),
                row.get("last_seen_at", OffsetDateTime.class).toInstant());
    }
}
