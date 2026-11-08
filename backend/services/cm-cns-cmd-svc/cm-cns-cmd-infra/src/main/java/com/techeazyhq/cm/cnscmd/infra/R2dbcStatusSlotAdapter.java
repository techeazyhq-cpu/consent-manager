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

import java.security.SecureRandom;

import org.springframework.r2dbc.core.DatabaseClient;

import com.techeazyhq.cm.cnscmd.dom.StatusSlotPort;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** {@link StatusSlotPort} adapter (ADR-077). */
public class R2dbcStatusSlotAdapter implements StatusSlotPort {

    private static final int ATTEMPTS = 12;

    private final DatabaseClient db;
    private final SecureRandom random = new SecureRandom();

    public R2dbcStatusSlotAdapter(DatabaseClient db) {
        this.db = db;
    }

    @Override
    public Mono<Integer> slotFor(String dfId, String cnsArtId) {
        return existing(cnsArtId).switchIfEmpty(Mono.defer(() -> claim(dfId, cnsArtId, ATTEMPTS)));
    }

    private Mono<Integer> existing(String cnsArtId) {
        return db.sql("select idx from cns_cmd.status_slot where cns_art_id = :art")
                .bind("art", cnsArtId).map((row, meta) -> row.get("idx", Integer.class)).one();
    }

    /** A random free index; a taken one (or a concurrent claim of the same consent) is simply drawn again. */
    private Mono<Integer> claim(String dfId, String cnsArtId, int attemptsLeft) {
        if (attemptsLeft == 0) {
            return Mono.error(new IllegalStateException("The status list of " + dfId + " has no free place left"));
        }
        int idx = random.nextInt(LIST_SIZE);
        return db.sql("""
                        insert into cns_cmd.status_slot (df_id, idx, cns_art_id) values (:df, :idx, :art)
                        on conflict do nothing returning idx
                        """)
                .bind("df", dfId).bind("idx", idx).bind("art", cnsArtId)
                .map((row, meta) -> row.get("idx", Integer.class)).one()
                .switchIfEmpty(Mono.defer(() -> existing(cnsArtId)))
                .switchIfEmpty(Mono.defer(() -> claim(dfId, cnsArtId, attemptsLeft - 1)));
    }

    @Override
    public Flux<Entry> entries(String dfId) {
        return db.sql("""
                        select s.idx, (a.id is null or a.sts <> 'ISSUED' or a.val_until <= now()) as ended
                        from cns_cmd.status_slot s left join cns_cmd.cns_art a on a.id = s.cns_art_id
                        where s.df_id = :df
                        """)
                .bind("df", dfId)
                .map((row, meta) -> new Entry(row.get("idx", Integer.class), Boolean.TRUE.equals(row.get("ended", Boolean.class))))
                .all();
    }
}
