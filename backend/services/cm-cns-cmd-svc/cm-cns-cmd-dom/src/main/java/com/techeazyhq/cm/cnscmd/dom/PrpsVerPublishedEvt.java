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
import java.util.List;
import java.util.UUID;

import com.techeazyhq.cm.domkit.DomainEvt;

/**
 * A fiduciary published a version of a purpose (ADR-031). Carries the classification, so principals can be told of a
 * non-material change and fiduciaries reminded that a material one needs re-consent. No personal data.
 */
public record PrpsVerPublishedEvt(UUID evtId, Instant occurredAt, String prpsDefId, String dfId, String prpsCode,
                                  String prpsVerId, int verNo, PrpsChange.Kind change, List<String> reasons)
        implements DomainEvt {

    static PrpsVerPublishedEvt of(PrpsDef def, PrpsVer ver) {
        return new PrpsVerPublishedEvt(UUID.randomUUID(), ver.publishedAt(), def.id(), def.dfId(), def.code(),
                ver.id(), ver.verNo(), ver.change().kind(), ver.change().reasons());
    }
}
