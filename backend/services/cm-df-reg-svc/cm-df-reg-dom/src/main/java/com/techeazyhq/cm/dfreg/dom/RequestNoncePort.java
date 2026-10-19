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

import reactor.core.publisher.Mono;

/**
 * Single-use nonces (ADR-041): a nonce is claimed for its key until {@code expiresAt}. The claim is atomic across
 * every instance, and a nonce whose claim has expired can be claimed again.
 */
public interface RequestNoncePort {

    /** True if this claim took the nonce; false if it was already taken and hasn't expired. */
    Mono<Boolean> claim(String keyId, String nonce, Instant expiresAt);

    /** Forgets claims that have expired. */
    Mono<Long> purgeExpired(Instant now);
}
