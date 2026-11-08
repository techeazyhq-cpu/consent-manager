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


package com.techeazyhq.cm.dfreg.dom;

import java.util.Map;

import reactor.core.publisher.Mono;

/** What the services that hold a fiduciary's consent records and audit entries give for its own data export (ADR-064). */
public interface DataExportPort {

    /** Its consent records with receipts, and its purposes, as the Consent context holds them. */
    Mono<Map<String, Object>> consentsAndPurposes(String dfId);

    /** Its audit ledger entries, with payloads. */
    Mono<Map<String, Object>> auditEntries(String dfId);
}
