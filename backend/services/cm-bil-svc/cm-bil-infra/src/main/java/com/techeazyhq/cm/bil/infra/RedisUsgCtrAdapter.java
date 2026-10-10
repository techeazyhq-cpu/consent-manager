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

package com.techeazyhq.cm.bil.infra;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

import com.techeazyhq.cm.bil.dom.UsgPort;

import reactor.core.publisher.Mono;

/**
 * {@link UsgPort} adapter over Redis {@code usg:{dfId}:{yyyy-MM}} counters (plan §11) —
 * the same counter {@code cm-gw}'s per-request filter increments (fire-and-forget) on every
 * DF-authenticated call.
 */
public class RedisUsgCtrAdapter implements UsgPort {

    private static final DateTimeFormatter PERIOD_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final ReactiveStringRedisTemplate redisTemplate;

    public RedisUsgCtrAdapter(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Long> currentPeriodUsage(String dfId) {
        String key = usageKey(dfId);
        return redisTemplate.opsForValue().get(key).map(Long::parseLong).defaultIfEmpty(0L);
    }

    public static String usageKey(String dfId) {
        return "usg:" + dfId + ":" + LocalDate.now(ZoneOffset.UTC).format(PERIOD_FORMAT);
    }
}
