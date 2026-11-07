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


package com.techeazyhq.cm.cnsqry.app;

import java.time.Instant;

import com.techeazyhq.cm.cnsqry.dom.DfRecordsPurgePort;

import reactor.core.publisher.Mono;

/** A closed fiduciary's records past their retention (ADR-064). Idempotent: run again, there is nothing more to delete. */
public class PurgeDfRecordsHandler {

    private final DfRecordsPurgePort records;

    public PurgeDfRecordsHandler(DfRecordsPurgePort records) {
        this.records = records;
    }

    public Mono<DfRecordsPurgePort.Purged> handle(String dfId, Instant before, int limit) {
        return records.purge(dfId, before, Math.max(1, Math.min(limit, 5000)));
    }
}
