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

/**
 * One version of a purpose (ADR-031). A draft may be edited or discarded; once published it never changes, so every
 * consent given for it can be traced to exactly what the principal was shown.
 *
 * @param change how it differs from the version before; null while a draft
 */
public record PrpsVer(String id, int verNo, Sts sts, PrpsVerContent content, PrpsChange change, Instant createdAt,
                      Instant publishedAt) {

    public enum Sts {
        DRAFT,
        PUBLISHED
    }

    public boolean isDraft() {
        return sts == Sts.DRAFT;
    }

    PrpsVer withContent(PrpsVerContent newContent) {
        return new PrpsVer(id, verNo, sts, newContent, change, createdAt, publishedAt);
    }

    PrpsVer published(PrpsChange how, Instant at) {
        return new PrpsVer(id, verNo, Sts.PUBLISHED, content, how, createdAt, at);
    }
}
