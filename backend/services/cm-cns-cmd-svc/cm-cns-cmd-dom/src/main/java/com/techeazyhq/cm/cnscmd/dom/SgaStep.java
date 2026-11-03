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

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

/**
 * One leg of the consent lifecycle saga (ADR-005) and where it stands: enough to run it again. {@code cnsId} is the
 * request's id, or the artefact's for a withdrawal; {@code dfId} and {@code outcome} are set for the legs that tell
 * the fiduciary. The id is derived from the leg and its subject, so a redelivered event finds the same row.
 */
public record SgaStep(String sagaId, String cnsId, Typ typ, String dfId, String outcome, Sts sts, int attempts,
                      String lastError, Instant updatedAt) {

    public enum Typ {
        NOTIFY_DP, DP_UNREACHABLE, NOTIFY_DF_DECIDED, NOTIFY_DF_REVOKED
    }

    /** STARTED and FAILED are not finished; the re-drive sweep takes them once stale, until it gives up. */
    public enum Sts {
        STARTED, DONE, FAILED, GAVE_UP
    }

    public static SgaStep of(Typ typ, String cnsId, String dfId, String outcome) {
        String sagaId = UUID.nameUUIDFromBytes((typ + ":" + cnsId).getBytes(StandardCharsets.UTF_8)).toString();
        return new SgaStep(sagaId, cnsId, typ, dfId, outcome, Sts.STARTED, 0, null, null);
    }
}
