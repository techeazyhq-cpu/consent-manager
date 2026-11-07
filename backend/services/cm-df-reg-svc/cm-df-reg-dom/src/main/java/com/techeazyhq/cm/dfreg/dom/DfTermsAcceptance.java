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

import java.time.Instant;
import java.util.UUID;

/**
 * Evidence that a named person accepted one version of one document for a fiduciary, and when. Never changed or removed:
 * a later version is a new acceptance, and the earlier one stays as the record of what was agreed then.
 */
public record DfTermsAcceptance(String id, String dfId, TermsDoc doc, String version, String acceptedBy, Instant acceptedAt) {

    public static DfTermsAcceptance of(String dfId, TermsDoc doc, String version, String acceptedBy, Instant at) {
        if (version == null || version.isBlank() || version.length() > 32) {
            throw new IllegalArgumentException("A document version of 1 to 32 characters is needed");
        }
        return new DfTermsAcceptance(UUID.randomUUID().toString(), dfId, doc, version.trim(), acceptedBy, at);
    }
}
