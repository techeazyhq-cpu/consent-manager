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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeazyhq.cm.cnsqry.dom.CnsArtRdMdl;
import com.techeazyhq.cm.cnsqry.dom.CnsArtRdMdlRepoPort;
import com.techeazyhq.cm.cnsqry.dom.CnsLcySts;
import com.techeazyhq.cm.cnsqry.dom.DpDashboardRow;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Read-through Redis cache in front of the read model (#31) for the lookups of one consent, by its artefact or its
 * request id: {@code cns:rdmdl:art:<id>} and {@code cns:rdmdl:req:<id>}, each for {@code ttl}. The projector writes
 * through this class, so each change evicts that consent's keys after the database has it.
 *
 * <p>The cache is never needed to answer. Redis slower than {@code timeout}, down, or holding something unreadable,
 * and the read goes to PostgreSQL. A miss is not cached: a consent not yet projected is found once it is. Lists,
 * counts and the dashboard are not cached. A read that started before a change can still put the old row back after
 * the eviction; {@code ttl} bounds how long that lasts.
 */
public class RedisCachedCnsArtRdMdlRepo implements CnsArtRdMdlRepoPort {

    private static final Logger LOG = LoggerFactory.getLogger(RedisCachedCnsArtRdMdlRepo.class);
    static final String ART_KEY = "cns:rdmdl:art:";
    static final String REQ_KEY = "cns:rdmdl:req:";

    private final CnsArtRdMdlRepoPort db;
    private final ReactiveStringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Duration ttl;
    private final Duration timeout;

    public RedisCachedCnsArtRdMdlRepo(CnsArtRdMdlRepoPort db, ReactiveStringRedisTemplate redis,
                                      ObjectMapper objectMapper, Duration ttl, Duration timeout) {
        this.db = db;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.ttl = ttl;
        this.timeout = timeout;
    }

    @Override
    public Mono<CnsArtRdMdl> findByCnsArtId(String cnsArtId) {
        return cached(ART_KEY + cnsArtId, () -> db.findByCnsArtId(cnsArtId));
    }

    @Override
    public Mono<CnsArtRdMdl> findByCnsReqId(String cnsReqId) {
        return cached(REQ_KEY + cnsReqId, () -> db.findByCnsReqId(cnsReqId));
    }

    @Override
    public Mono<CnsArtRdMdl> upsert(CnsArtRdMdl row) {
        return db.upsert(row).flatMap(saved -> evict(row).thenReturn(saved));
    }

    /** The row is found again after the change, for the key the linked id isn't. */
    @Override
    public Mono<Void> linkDp(String recordId, String recordType, String newDpId) {
        Mono<CnsArtRdMdl> row = "CnsReq".equals(recordType) ? db.findByCnsReqId(recordId) : db.findByCnsArtId(recordId);
        return db.linkDp(recordId, recordType, newDpId)
                .then(row.flatMap(this::evict))
                .then(evictKeys(List.of(("CnsReq".equals(recordType) ? REQ_KEY : ART_KEY) + recordId)));
    }

    @Override
    public Flux<CnsArtRdMdl> findByDpId(String dpId) {
        return db.findByDpId(dpId);
    }

    @Override
    public Flux<CnsArtRdMdl> findByDfId(String dfId) {
        return db.findByDfId(dfId);
    }

    @Override
    public Mono<Map<CnsLcySts, Long>> countByStsForDfId(String dfId) {
        return db.countByStsForDfId(dfId);
    }

    @Override
    public Flux<CnsArtRdMdl> pageByDfId(String dfId, CnsLcySts sts, int limit, long offset) {
        return db.pageByDfId(dfId, sts, limit, offset);
    }

    @Override
    public Flux<DpDashboardRow> dashboardFor(String dpId) {
        return db.dashboardFor(dpId);
    }

    private Mono<CnsArtRdMdl> cached(String key, java.util.function.Supplier<Mono<CnsArtRdMdl>> load) {
        return redis.opsForValue().get(key)
                .timeout(timeout)
                .map(this::fromJson)
                .onErrorResume(e -> {
                    LOG.debug("Read-model cache unavailable for {}, reading the database: {}", key, e.toString());
                    return Mono.empty();
                })
                .switchIfEmpty(Mono.defer(load).flatMap(row -> put(key, row).thenReturn(row)));
    }

    private Mono<Void> put(String key, CnsArtRdMdl row) {
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(row))
                .flatMap(json -> redis.opsForValue().set(key, json, ttl))
                .timeout(timeout)
                .onErrorResume(e -> Mono.empty())
                .then();
    }

    private Mono<Void> evict(CnsArtRdMdl row) {
        List<String> keys = new ArrayList<>(2);
        if (row.cnsReqId() != null) {
            keys.add(REQ_KEY + row.cnsReqId());
        }
        if (row.cnsArtId() != null) {
            keys.add(ART_KEY + row.cnsArtId());
        }
        return evictKeys(keys);
    }

    /**
     * An eviction that can't reach Redis is logged, not failed: the change is already in the database, and failing
     * would only have the event redelivered to the same unreachable cache. The entry then lapses with its TTL.
     */
    private Mono<Void> evictKeys(List<String> keys) {
        if (keys.isEmpty()) {
            return Mono.empty();
        }
        return redis.delete(keys.toArray(String[]::new))
                .timeout(timeout)
                .onErrorResume(e -> {
                    LOG.warn("Could not evict {} from the read-model cache; it lapses within its TTL: {}", keys,
                            e.toString());
                    return Mono.empty();
                })
                .then();
    }

    private CnsArtRdMdl fromJson(String json) {
        try {
            return objectMapper.readValue(json, CnsArtRdMdl.class);
        } catch (Exception e) {
            throw new IllegalStateException("Unreadable read-model cache entry", e);
        }
    }
}
