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

package com.techeazyhq.cm.ntf.app;

import com.techeazyhq.cm.ntf.dom.PushDvc;
import com.techeazyhq.cm.ntf.dom.PushDvcRepoPort;
import com.techeazyhq.cm.ntf.dom.PushPltf;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** A DP registering, listing and removing the browsers they want consent notifications pushed to. */
public class PushDvcCmdHandler {

    private final PushDvcRepoPort repo;

    public PushDvcCmdHandler(PushDvcRepoPort repo) {
        this.repo = repo;
    }

    public Mono<PushDvc> register(String dpId, String token, PushPltf platform) {
        if (token == null || token.isBlank() || token.length() > PushDvc.MAX_TOKEN_LENGTH) {
            return Mono.error(new DomRuleViolation(400, "push-token-invalid",
                    "token must be 1-" + PushDvc.MAX_TOKEN_LENGTH + " characters"));
        }
        return repo.upsert(dpId, token, platform == null ? PushPltf.WEB : platform);
    }

    public Flux<PushDvc> list(String dpId) {
        return repo.findByDpId(dpId);
    }

    public Mono<Void> remove(String dpId, String id) {
        return repo.delete(dpId, id)
                .flatMap(existed -> existed ? Mono.<Void>empty()
                        : Mono.error(new DomRuleViolation(404, "push-dvc-not-found", "No such device: " + id)));
    }
}
