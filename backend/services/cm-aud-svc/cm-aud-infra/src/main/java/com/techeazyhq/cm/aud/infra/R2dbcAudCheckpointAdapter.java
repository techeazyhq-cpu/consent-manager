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

import com.techeazyhq.cm.aud.dom.AudAnchor;
import com.techeazyhq.cm.aud.dom.AudCheckpoint;
import com.techeazyhq.cm.aud.dom.AudCheckpointPort;
import io.r2dbc.spi.Row;
import org.springframework.r2dbc.core.DatabaseClient;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;
import java.time.OffsetDateTime;

/** Checkpoints and anchors in PostgreSQL ({@code aud.checkpoint}, {@code aud.checkpoint_anchor}), both append-only. */
public class R2dbcAudCheckpointAdapter implements AudCheckpointPort {

    private static final String CHECKPOINT_COLUMNS = "c.id, c.from_seq, c.to_seq, c.merkle_root, c.head_hash, "
            + "c.prev_checkpoint_hash, c.created_at, c.checkpoint_hash";
    private static final String WITH_ANCHOR = "select " + CHECKPOINT_COLUMNS
            + ", a.authority_url, a.timestamped_at, a.token from aud.checkpoint c "
            + "left join aud.checkpoint_anchor a on a.checkpoint_id = c.id";

    private final DatabaseClient db;

    public R2dbcAudCheckpointAdapter(DatabaseClient db) {
        this.db = db;
    }

    @Override
    public Mono<AudCheckpoint> latest() {
        return db.sql("select " + CHECKPOINT_COLUMNS + " from aud.checkpoint c order by c.id desc limit 1")
                .map((row, meta) -> toCheckpoint(row))
                .one();
    }

    @Override
    public Mono<Void> save(AudCheckpoint cp) {
        return db.sql("""
                        insert into aud.checkpoint (id, from_seq, to_seq, merkle_root, head_hash, prev_checkpoint_hash,
                                                    created_at, checkpoint_hash)
                        values (:id, :fromSeq, :toSeq, :merkleRoot, :headHash, :prevHash, :createdAt, :hash)""")
                .bind("id", cp.id())
                .bind("fromSeq", cp.fromSeq())
                .bind("toSeq", cp.toSeq())
                .bind("merkleRoot", cp.merkleRoot())
                .bind("headHash", cp.headHash())
                .bind("prevHash", cp.prevCheckpointHash())
                .bind("createdAt", cp.createdAt())
                .bind("hash", cp.checkpointHash())
                .then();
    }

    @Override
    public Mono<Void> saveAnchor(AudAnchor anchor) {
        return db.sql("""
                        insert into aud.checkpoint_anchor (checkpoint_id, authority_url, timestamped_at, token)
                        values (:id, :url, :at, :token)""")
                .bind("id", anchor.checkpointId())
                .bind("url", anchor.authorityUrl())
                .bind("at", anchor.timestampedAt())
                .bind("token", ByteBuffer.wrap(anchor.token()))
                .then();
    }

    @Override
    public Flux<AudCheckpoint> unanchored() {
        return db.sql("select " + CHECKPOINT_COLUMNS + " from aud.checkpoint c where not exists "
                        + "(select 1 from aud.checkpoint_anchor a where a.checkpoint_id = c.id) order by c.id")
                .map((row, meta) -> toCheckpoint(row))
                .all();
    }

    @Override
    public Flux<Anchored> all() {
        return db.sql(WITH_ANCHOR + " order by c.id").map((row, meta) -> toAnchored(row)).all();
    }

    @Override
    public Mono<Anchored> covering(long seq) {
        return db.sql(WITH_ANCHOR + " where :seq between c.from_seq and c.to_seq")
                .bind("seq", seq)
                .map((row, meta) -> toAnchored(row))
                .one();
    }

    private static Anchored toAnchored(Row row) {
        AudCheckpoint cp = toCheckpoint(row);
        ByteBuffer token = row.get("token", ByteBuffer.class);
        if (token == null) {
            return new Anchored(cp, null);
        }
        byte[] bytes = new byte[token.remaining()];
        token.get(bytes);
        return new Anchored(cp, new AudAnchor(cp.id(), row.get("authority_url", String.class),
                row.get("timestamped_at", OffsetDateTime.class).toInstant(), bytes));
    }

    private static AudCheckpoint toCheckpoint(Row row) {
        return new AudCheckpoint(
                row.get("id", Long.class),
                row.get("from_seq", Long.class),
                row.get("to_seq", Long.class),
                row.get("merkle_root", String.class),
                row.get("head_hash", String.class),
                row.get("prev_checkpoint_hash", String.class),
                row.get("created_at", OffsetDateTime.class).toInstant(),
                row.get("checkpoint_hash", String.class));
    }
}
