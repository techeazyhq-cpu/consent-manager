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

package com.techeazyhq.cm.aud.app;

import java.util.List;
import java.util.Map;

import com.techeazyhq.cm.aud.dom.AudLedgerPort;

import reactor.core.publisher.Mono;

/**
 * The audit ledger's step of a principal closing their account (ADR-059): their payloads and subject links go, the
 * entries and the chain stay. Idempotent: run again, there is nothing left to remove.
 */
public class AudErasureService {

    private final AudLedgerPort ledger;

    public AudErasureService(AudLedgerPort ledger) {
        this.ledger = ledger;
    }

    public Mono<Map<String, Object>> erase(String dpId, List<String> cnsReqIds) {
        return ledger.eraseAbout(dpId, cnsReqIds == null ? List.of() : cnsReqIds)
                .map(removed -> Map.<String, Object>of("removed", removed));
    }
}
