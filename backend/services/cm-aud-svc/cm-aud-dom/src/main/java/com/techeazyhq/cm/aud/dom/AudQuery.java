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

package com.techeazyhq.cm.aud.dom;

/**
 * What to look up in the ledger; null fields do not filter. Whoever asks, the interface layer fixes {@code dfId} or
 * {@code dpId} to the caller's own when the caller is a fiduciary or a principal.
 */
public record AudQuery(String cnsReqId, String cnsArtId, String dfId, String dpId, int limit, Category category,
                       Long beforeSeq) {

    /** The consent lifecycle's events, or what people did on the platform (ADR-040), or both. */
    public enum Category {
        ALL, CONSENT, PLATFORM
    }

    public static final int MAX_LIMIT = 500;

    public AudQuery {
        limit = limit <= 0 ? 100 : Math.min(limit, MAX_LIMIT);
        category = category == null ? Category.ALL : category;
    }

    public AudQuery(String cnsReqId, String cnsArtId, String dfId, String dpId, int limit) {
        this(cnsReqId, cnsArtId, dfId, dpId, limit, Category.ALL, null);
    }
}
