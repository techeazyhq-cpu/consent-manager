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

package com.techeazyhq.cm.dfreg.infra;

import org.springframework.r2dbc.core.DatabaseClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeazyhq.cm.dfreg.dom.ObxPort;
import com.techeazyhq.cm.domkit.DomainEvt;

import reactor.core.publisher.Mono;

public class R2dbcObxAdapter implements ObxPort {

    private final ObxWriter writer;

    public R2dbcObxAdapter(DatabaseClient db, ObjectMapper objectMapper) {
        this.writer = new ObxWriter(db, objectMapper);
    }

    @Override
    public Mono<Void> enqueue(DomainEvt evt, String aggType, String aggId) {
        return writer.insert(evt, aggType, aggId);
    }
}
