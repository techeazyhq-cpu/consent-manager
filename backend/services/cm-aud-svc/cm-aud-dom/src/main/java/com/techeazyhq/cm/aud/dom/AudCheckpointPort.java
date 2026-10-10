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

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Port: checkpoints and their anchors, both append-only. Implemented by {@code R2dbcAudCheckpointAdapter}. */
public interface AudCheckpointPort {

    Mono<AudCheckpoint> latest();

    Mono<Void> save(AudCheckpoint checkpoint);

    Mono<Void> saveAnchor(AudAnchor anchor);

    /** Checkpoints no authority has timestamped yet, oldest first. */
    Flux<AudCheckpoint> unanchored();

    /** Every checkpoint in order, each with its anchor if it has one. */
    Flux<Anchored> all();

    /** The checkpoint whose range holds entry {@code seq}, with its anchor if it has one. */
    Mono<Anchored> covering(long seq);

    /** A checkpoint and its anchor; {@code anchor} is null until a timestamp authority has signed it. */
    record Anchored(AudCheckpoint checkpoint, AudAnchor anchor) {
    }
}
