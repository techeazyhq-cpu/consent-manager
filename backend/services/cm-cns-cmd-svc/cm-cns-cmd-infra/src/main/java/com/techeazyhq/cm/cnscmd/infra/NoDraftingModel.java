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

package com.techeazyhq.cm.cnscmd.infra;

import com.techeazyhq.cm.cnscmd.dom.DraftingModelPort;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Mono;

/** Drafting with a model is off, which it is until an operator points the platform at a model (ADR-111). */
public class NoDraftingModel implements DraftingModelPort {

    @Override
    public Status status() {
        return new Status(false, null);
    }

    @Override
    public Mono<String> complete(String system, String user) {
        return Mono.error(new DomRuleViolation(404, "ai-drafting-disabled", "Drafting with a model is not switched on for this platform"));
    }
}
