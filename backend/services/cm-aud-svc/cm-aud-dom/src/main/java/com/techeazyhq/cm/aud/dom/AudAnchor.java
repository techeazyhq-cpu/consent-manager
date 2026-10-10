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

import java.time.Instant;

/**
 * An external timestamp authority's RFC 3161 token over a checkpoint's hash (ADR-015): proof, signed by a third party,
 * that the ledger was in that state no later than {@code timestampedAt}.
 *
 * @param token the DER-encoded TimeStampToken, as the authority returned it
 */
public record AudAnchor(long checkpointId, String authorityUrl, Instant timestampedAt, byte[] token) {
}
