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

import java.time.Instant;
import java.util.UUID;

import com.techeazyhq.cm.domkit.DomainEvt;

/** Stage 2 decided: deny (plan §7c step 3). */
public record CnsDeniedEvt(UUID evtId, Instant occurredAt, String cnsReqId, String dfId, DpRef dpRef)
        implements DomainEvt {

    public static CnsDeniedEvt of(String cnsReqId, String dfId, DpRef dpRef) {
        return new CnsDeniedEvt(UUID.randomUUID(), Instant.now(), cnsReqId, dfId, dpRef);
    }
}
