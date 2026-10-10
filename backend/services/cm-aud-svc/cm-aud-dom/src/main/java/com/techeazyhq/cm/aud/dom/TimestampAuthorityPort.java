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

import reactor.core.publisher.Mono;

/** Port: an RFC 3161 timestamp authority (ADR-015). Implemented by {@code Rfc3161TimestampAuthority}. */
public interface TimestampAuthorityPort {

    /** Has the authority timestamp the SHA-256 given as hex; fails if it refuses or answers with a token for anything else. */
    Mono<AudAnchor> timestamp(long checkpointId, String sha256Hex);

    /**
     * The SHA-256 the token was issued for, as hex, after checking the token's signature against the certificate it
     * carries; fails for a token that is malformed or not validly signed.
     */
    String verifiedImprint(byte[] token);
}
