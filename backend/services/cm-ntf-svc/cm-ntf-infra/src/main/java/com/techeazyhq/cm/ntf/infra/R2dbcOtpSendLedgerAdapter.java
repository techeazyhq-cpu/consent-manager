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

package com.techeazyhq.cm.ntf.infra;

import java.util.concurrent.ThreadLocalRandom;

import org.springframework.r2dbc.core.DatabaseClient;

import com.techeazyhq.cm.ntf.dom.NtfChnl;
import com.techeazyhq.cm.ntf.dom.OtpSendLedgerPort;

import reactor.core.publisher.Mono;

/**
 * {@link OtpSendLedgerPort} in PostgreSQL (ADR-080): the count and the new row are one statement, so requests that arrive together
 * see each other's rows except in the instant between two statements; at worst the limit is passed by the number of requests in
 * that instant, which is a backstop's tolerance, not a leak.
 */
public class R2dbcOtpSendLedgerAdapter implements OtpSendLedgerPort {

    private final DatabaseClient db;

    public R2dbcOtpSendLedgerAdapter(DatabaseClient db) {
        this.db = db;
    }

    @Override
    public Mono<Long> claim(String destinationHash, NtfChnl channel, Limits limits) {
        return db.sql("""
                        insert into ntf.otp_send (dest_hash, channel)
                        select :hash, :channel
                        where (:perHour = 0 or (select count(*) from ntf.otp_send
                                                 where dest_hash = :hash and sent_at > now() - interval '1 hour') < :perHour)
                          and (:perDay = 0 or (select count(*) from ntf.otp_send
                                                where dest_hash = :hash and sent_at > now() - interval '1 day') < :perDay)
                          and (:perChannel = 0 or (select count(*) from ntf.otp_send
                                                    where channel = :channel and sent_at > now() - interval '1 hour') < :perChannel)
                        returning id
                        """)
                .bind("hash", destinationHash).bind("channel", channel.name())
                .bind("perHour", limits.perDestinationPerHour()).bind("perDay", limits.perDestinationPerDay())
                .bind("perChannel", limits.perChannelPerHour())
                .map((row, meta) -> row.get("id", Long.class)).one()
                .flatMap(id -> prune().thenReturn(id));
    }

    @Override
    public Mono<Void> release(long place) {
        return db.sql("delete from ntf.otp_send where id = :id").bind("id", place).fetch().rowsUpdated().then();
    }

    /** Rows older than two days count in no limit; about one send in a hundred clears them. */
    private Mono<Void> prune() {
        if (ThreadLocalRandom.current().nextInt(100) != 0) {
            return Mono.empty();
        }
        return db.sql("delete from ntf.otp_send where sent_at < now() - interval '2 days'").fetch().rowsUpdated().then();
    }
}
