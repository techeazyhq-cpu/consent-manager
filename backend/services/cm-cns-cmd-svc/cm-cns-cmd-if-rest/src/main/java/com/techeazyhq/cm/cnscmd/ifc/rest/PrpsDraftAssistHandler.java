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

package com.techeazyhq.cm.cnscmd.ifc.rest;

import java.util.List;

import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.techeazyhq.cm.cnscmd.app.PrpsDraftAssist;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Mono;

/**
 * Drafting a purpose from a description with the operator's own model (ADR-111).
 * <ul>
 *   <li>{@code GET /api/cns-cmd/prps-defs/draft-assist/status}: whether it is switched on, with which model and how long a description may be. Any of the
 *       fiduciary's people, so the console knows whether to offer it.</li>
 *   <li>{@code POST /api/cns-cmd/prps-defs/draft-assist}: {@code {description, lang}}. A suggestion, never saved: the content of a purpose
 *       version as the ordinary draft takes it, with the fields a person must confirm and warnings. Those who manage purposes only.</li>
 * </ul>
 */
public class PrpsDraftAssistHandler {

    private final PrpsDraftAssist assist;

    public PrpsDraftAssistHandler(PrpsDraftAssist assist) {
        this.assist = assist;
    }

    public Mono<ServerResponse> status(ServerRequest request) {
        return PrpsDefHttpHandler.asDf(request, false, ctx -> {
            var s = assist.status();
            return ServerResponse.ok().bodyValue(new StatusDto(s.enabled(), s.model(), PrpsDraftAssist.MIN_INPUT, assist.maxInput()));
        });
    }

    public Mono<ServerResponse> draft(ServerRequest request) {
        return PrpsDefHttpHandler.asDf(request, true, ctx -> request.bodyToMono(DraftReqDto.class)
                .switchIfEmpty(Mono.error(() -> new DomRuleViolation(400, "invalid-purpose", "Describe the purpose: description")))
                .flatMap(body -> assist.draft(ctx.dfId(), ctx.subject(), body.description(), body.lang()))
                .flatMap(s -> ServerResponse.ok().bodyValue(new SuggestionDto(s.code(), PrpsDefHttpHandler.ContentDto.of(s.content()), s.confirm(),
                        s.notes(), s.model(), true))));
    }

    record DraftReqDto(String description, String lang) {
    }

    record StatusDto(boolean enabled, String model, int minChars, int maxChars) {
    }

    /** {@code content} is what {@code POST /prps-defs} and {@code PUT .../draft} take; {@code aiGenerated} is always true. */
    record SuggestionDto(String code, PrpsDefHttpHandler.ContentDto content, List<String> confirm, List<String> notes, String model,
                         boolean aiGenerated) {
    }
}
