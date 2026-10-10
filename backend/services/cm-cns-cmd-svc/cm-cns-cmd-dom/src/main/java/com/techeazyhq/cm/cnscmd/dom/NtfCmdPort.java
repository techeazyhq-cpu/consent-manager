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

package com.techeazyhq.cm.cnscmd.dom;

import reactor.core.publisher.Mono;

/** Port to cm-ntf-svc (plan §7c steps 2/4): implemented in -infra as a Pulsar producer to its command topics. */
public interface NtfCmdPort {

    Mono<Void> notifyDp(CnsReq cnsReq);

    /** {@code outcome} is a free-form label (e.g. "GRANTED", "DENIED", "REVOKED") — the notification's own concern, not a write-side status enum. */
    Mono<Void> notifyDf(String dfId, String subjectId, String outcome);
}
