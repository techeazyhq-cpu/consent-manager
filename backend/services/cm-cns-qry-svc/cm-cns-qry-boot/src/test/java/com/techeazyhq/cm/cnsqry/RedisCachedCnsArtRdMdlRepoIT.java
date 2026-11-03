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

package com.techeazyhq.cm.cnsqry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeazyhq.cm.cnsqry.dom.CnsArtRdMdl;
import com.techeazyhq.cm.cnsqry.dom.CnsLcySts;
import com.techeazyhq.cm.cnsqry.infra.R2dbcCnsArtRdMdlRepoAdapter;
import com.techeazyhq.cm.cnsqry.infra.RedisCachedCnsArtRdMdlRepo;
import com.techeazyhq.cm.testkit.PgIntegrationTestBase;

import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactoryOptions;

/**
 * The read-model cache (#31) against a real PostgreSQL and Redis: a lookup is read through and then served from
 * Redis, the projector's writes evict it, a miss isn't cached, and without Redis every read still answers from the
 * database.
 */
@Testcontainers
class RedisCachedCnsArtRdMdlRepoIT extends PgIntegrationTestBase {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private static final Duration TTL = Duration.ofSeconds(60);

    private static DatabaseClient db;
    private static LettuceConnectionFactory redisConnection;
    private static LettuceConnectionFactory deadRedisConnection;
    private static ReactiveStringRedisTemplate redis;
    private static R2dbcCnsArtRdMdlRepoAdapter database;
    private static RedisCachedCnsArtRdMdlRepo cached;

    @BeforeAll
    static void setUp() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("cns_qry").defaultSchema("cns_qry").locations("classpath:db/migration")
                .load().migrate();
        db = DatabaseClient.create(ConnectionFactories.get(ConnectionFactoryOptions.builder()
                .option(ConnectionFactoryOptions.DRIVER, "postgresql")
                .option(ConnectionFactoryOptions.HOST, POSTGRES.getHost())
                .option(ConnectionFactoryOptions.PORT, POSTGRES.getFirstMappedPort())
                .option(ConnectionFactoryOptions.DATABASE, POSTGRES.getDatabaseName())
                .option(ConnectionFactoryOptions.USER, POSTGRES.getUsername())
                .option(ConnectionFactoryOptions.PASSWORD, POSTGRES.getPassword())
                .build()));
        redisConnection = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        redisConnection.afterPropertiesSet();
        redis = new ReactiveStringRedisTemplate(redisConnection);
        database = new R2dbcCnsArtRdMdlRepoAdapter(db, JSON);
        cached = new RedisCachedCnsArtRdMdlRepo(database, redis, JSON, TTL, Duration.ofMillis(500));
    }

    @AfterAll
    static void tearDown() {
        redisConnection.destroy();
        if (deadRedisConnection != null) {
            deadRedisConnection.destroy();
        }
    }

    @Test
    void aLookupIsReadThroughAndThenServedFromTheCache() {
        CnsArtRdMdl row = cached.upsert(issued(id("req"), id("art"), "dp-1")).block();

        assertEquals(CnsLcySts.ISSUED, cached.findByCnsArtId(row.cnsArtId()).block().lcySts());
        Long ttl = redis.getExpire("cns:rdmdl:art:" + row.cnsArtId()).map(Duration::getSeconds).block();
        assertTrue(ttl != null && ttl > 0 && ttl <= TTL.getSeconds(), "cached for at most the TTL: " + ttl);

        // changed behind the cache's back: the cache still answers, so the second read never reached the database
        db.sql("update cns_qry.cns_art_rd_mdl set lcy_sts = 'REVOKED' where cns_art_id = :id")
                .bind("id", row.cnsArtId()).then().block();
        assertEquals(CnsLcySts.ISSUED, cached.findByCnsArtId(row.cnsArtId()).block().lcySts());
        assertEquals(CnsLcySts.REVOKED, database.findByCnsArtId(row.cnsArtId()).block().lcySts());
    }

    @Test
    void theProjectorsWriteEvictsBothKeys() {
        CnsArtRdMdl row = cached.upsert(issued(id("req"), id("art"), "dp-1")).block();
        cached.findByCnsArtId(row.cnsArtId()).block();
        cached.findByCnsReqId(row.cnsReqId()).block();

        cached.upsert(withStatus(row, CnsLcySts.REVOKED)).block();

        assertFalse(exists("cns:rdmdl:art:" + row.cnsArtId()));
        assertFalse(exists("cns:rdmdl:req:" + row.cnsReqId()));
        assertEquals(CnsLcySts.REVOKED, cached.findByCnsArtId(row.cnsArtId()).block().lcySts());
        assertEquals(CnsLcySts.REVOKED, cached.findByCnsReqId(row.cnsReqId()).block().lcySts());
    }

    @Test
    void linkingAPrincipalEvictsTheConsentUnderBothItsIds() {
        CnsArtRdMdl row = cached.upsert(issued(id("req"), id("art"), null)).block();
        assertNull(cached.findByCnsArtId(row.cnsArtId()).block().dpId());
        cached.findByCnsReqId(row.cnsReqId()).block();

        cached.linkDp(row.cnsReqId(), "CnsReq", "dp-new").block();

        assertEquals("dp-new", cached.findByCnsArtId(row.cnsArtId()).block().dpId());
        assertEquals("dp-new", cached.findByCnsReqId(row.cnsReqId()).block().dpId());
    }

    @Test
    void aConsentNotYetProjectedIsNotCachedAsMissing() {
        String req = id("req");

        assertNull(cached.findByCnsReqId(req).block());
        assertFalse(exists("cns:rdmdl:req:" + req));

        database.upsert(issued(req, id("art"), "dp-1")).block();
        assertEquals(req, cached.findByCnsReqId(req).block().cnsReqId());
    }

    @Test
    void anUnreadableEntryIsIgnoredForTheDatabase() {
        CnsArtRdMdl row = database.upsert(issued(id("req"), id("art"), "dp-1")).block();
        redis.opsForValue().set("cns:rdmdl:art:" + row.cnsArtId(), "not json").block();

        assertEquals(row.cnsReqId(), cached.findByCnsArtId(row.cnsArtId()).block().cnsReqId());
    }

    @Test
    void withoutRedisEveryReadAndWriteStillWorks() {
        deadRedisConnection = new LettuceConnectionFactory("127.0.0.1", 1);
        deadRedisConnection.afterPropertiesSet();
        RedisCachedCnsArtRdMdlRepo noCache = new RedisCachedCnsArtRdMdlRepo(database,
                new ReactiveStringRedisTemplate(deadRedisConnection), JSON, TTL, Duration.ofMillis(200));

        CnsArtRdMdl row = noCache.upsert(issued(id("req"), id("art"), "dp-1")).block(Duration.ofSeconds(10));
        assertEquals(row.cnsReqId(), noCache.findByCnsArtId(row.cnsArtId()).block(Duration.ofSeconds(10)).cnsReqId());
        noCache.upsert(withStatus(row, CnsLcySts.REVOKED)).block(Duration.ofSeconds(10));
        assertEquals(CnsLcySts.REVOKED, noCache.findByCnsReqId(row.cnsReqId()).block(Duration.ofSeconds(10)).lcySts());
    }

    private static CnsArtRdMdl issued(String req, String art, String dpId) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        return new CnsArtRdMdl(req, art, "df-a", dpId, "ORDER_UPDATES", Set.of("EMAIL", "NAME"), CnsLcySts.ISSUED,
                now, now.plus(Duration.ofDays(30)), now, now, "Order updates", "We email you about your orders.",
                Set.of("EMAIL"));
    }

    private static CnsArtRdMdl withStatus(CnsArtRdMdl row, CnsLcySts sts) {
        return row.with(row.cnsArtId(), row.dpId(), sts, row.validFrom(), row.validUntil(),
                row.updatedAt().plusSeconds(1));
    }

    private static boolean exists(String key) {
        return Boolean.TRUE.equals(redis.hasKey(key).block());
    }

    private static String id(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
