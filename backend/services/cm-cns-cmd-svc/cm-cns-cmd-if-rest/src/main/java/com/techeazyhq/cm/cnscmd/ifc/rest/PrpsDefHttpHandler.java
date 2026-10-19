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

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.techeazyhq.cm.cnscmd.app.PrpsDefCmds;
import com.techeazyhq.cm.cnscmd.dom.PrpsChange;
import com.techeazyhq.cm.cnscmd.dom.PrpsDCat;
import com.techeazyhq.cm.cnscmd.dom.PrpsDef;
import com.techeazyhq.cm.cnscmd.dom.PrpsFlag;
import com.techeazyhq.cm.cnscmd.dom.PrpsNtc;
import com.techeazyhq.cm.cnscmd.dom.PrpsSensitivity;
import com.techeazyhq.cm.cnscmd.dom.PrpsVer;
import com.techeazyhq.cm.cnscmd.dom.PrpsVerContent;
import com.techeazyhq.cm.seckit.AuthCtx;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Mono;

/**
 * A fiduciary's purposes and their versions (ADR-031), for its people and its systems alike.
 * <ul>
 *   <li>{@code GET /api/cns-cmd/prps-defs}: every purpose, with its current version and any draft.</li>
 *   <li>{@code POST /api/cns-cmd/prps-defs}: a new purpose, {@code code} and {@code content}; its version 1 is a
 *       draft.</li>
 *   <li>{@code GET .../prps-defs/{code}}: the purpose with every version.</li>
 *   <li>{@code PUT .../{code}/draft}: start or replace the draft of the next version; {@code DELETE} discards it.</li>
 *   <li>{@code GET .../{code}/draft/change}: how the draft differs from the current version, before publishing.</li>
 *   <li>{@code POST .../{code}/publish}: the draft becomes the current version.</li>
 * </ul>
 * Reading is open to all the fiduciary's people; changes to Owners, Administrators, Consent managers and systems
 * (ADR-033).
 */
public class PrpsDefHttpHandler {

    private final PrpsDefCmds cmds;

    public PrpsDefHttpHandler(PrpsDefCmds cmds) {
        this.cmds = cmds;
    }

    public Mono<ServerResponse> list(ServerRequest request) {
        return asDf(request, false, ctx -> cmds.list(ctx.dfId()).map(SummaryDto::of).collectList()
                .flatMap(list -> ServerResponse.ok().bodyValue(list)));
    }

    public Mono<ServerResponse> get(ServerRequest request) {
        return asDf(request, false, ctx -> cmds.get(ctx.dfId(), code(request))
                .flatMap(def -> ServerResponse.ok().bodyValue(DefDto.of(def))));
    }

    public Mono<ServerResponse> create(ServerRequest request) {
        return asDf(request, true, ctx -> request.bodyToMono(CreateDto.class)
                .switchIfEmpty(Mono.error(() -> new DomRuleViolation(400, "invalid-purpose", "The request body is empty")))
                .flatMap(body -> cmds.create(ctx.dfId(), body.code() == null ? null : body.code().strip(),
                        content(body.content())))
                .flatMap(def -> ServerResponse.status(HttpStatus.CREATED).bodyValue(DefDto.of(def))));
    }

    public Mono<ServerResponse> saveDraft(ServerRequest request) {
        return asDf(request, true, ctx -> request.bodyToMono(ContentDto.class)
                .switchIfEmpty(Mono.error(() -> new DomRuleViolation(400, "invalid-purpose", "The request body is empty")))
                .flatMap(body -> cmds.saveDraft(ctx.dfId(), code(request), content(body)))
                .flatMap(def -> ServerResponse.ok().bodyValue(DefDto.of(def))));
    }

    public Mono<ServerResponse> discardDraft(ServerRequest request) {
        return asDf(request, true, ctx -> cmds.discardDraft(ctx.dfId(), code(request))
                .flatMap(def -> ServerResponse.ok().bodyValue(DefDto.of(def))));
    }

    public Mono<ServerResponse> draftChange(ServerRequest request) {
        return asDf(request, false, ctx -> cmds.get(ctx.dfId(), code(request))
                .flatMap(def -> def.draft().isEmpty()
                        ? Mono.error(new DomRuleViolation(404, "no-draft", def.code() + " has no draft"))
                        : ServerResponse.ok().bodyValue(ChangeDto.of(def.draftChange()))));
    }

    public Mono<ServerResponse> publish(ServerRequest request) {
        return asDf(request, true, ctx -> cmds.publish(ctx.dfId(), code(request))
                .flatMap(def -> ServerResponse.ok().bodyValue(DefDto.of(def))));
    }

    private static String code(ServerRequest request) {
        return request.pathVariable("code");
    }

    /** Fiduciary callers only; changing purposes needs a role that manages them (ADR-033), reading any of its people. */
    private static Mono<ServerResponse> asDf(ServerRequest request, boolean changes,
                                             Function<AuthCtx, Mono<ServerResponse>> action) {
        return CnsReqHandler.authCtx(request)
                .filter(AuthCtx::isDf)
                .flatMap(ctx -> changes && !ctx.mayManagePurposes()
                        ? Mono.<ServerResponse>error(new DomRuleViolation(403, "not-permitted",
                                "Your role at this fiduciary doesn't manage purposes"))
                        : action.apply(ctx))
                .switchIfEmpty(Mono.defer(() -> ServerResponse.status(HttpStatus.FORBIDDEN).build()));
    }

    private static PrpsVerContent content(ContentDto dto) {
        if (dto == null) {
            throw new DomRuleViolation(400, "invalid-purpose", "Describe the purpose: content");
        }
        return dto.toDomain();
    }

    // ---- wire formats ----

    record DCatDto(String code, String dpv, String sensitivity, Boolean necessary, String consequence) {

        PrpsDCat toDomain() {
            return new PrpsDCat(code == null ? null : code.strip(), blankToNull(dpv),
                    sensitivity == null || sensitivity.isBlank() ? null : parse(PrpsSensitivity.class, sensitivity),
                    Boolean.TRUE.equals(necessary), blankToNull(consequence));
        }

        static DCatDto of(PrpsDCat d) {
            return new DCatDto(d.code(), d.dpv(), d.sensitivity().name(), d.necessary(), d.consequence());
        }
    }

    record NtcDto(String lang, String title, String text, String hash) {

        static NtcDto of(PrpsNtc n) {
            return new NtcDto(n.lang(), n.title(), n.text(), n.hash());
        }
    }

    /** What a purpose version says; {@code hash} on a notice is ignored on the way in. */
    record ContentDto(String name, String description, List<DCatDto> dCats, List<String> recipients,
                      List<String> transfers, Integer retentionDays, Integer requestedValidityDays,
                      Integer maxValidityDays, List<String> flags, List<NtcDto> notices) {

        PrpsVerContent toDomain() {
            return new PrpsVerContent(name, blankToNull(description),
                    dCats == null ? List.of() : dCats.stream().map(d -> d == null ? null : d.toDomain()).toList(),
                    recipients, transfers, orZero(retentionDays), orZero(requestedValidityDays),
                    orZero(maxValidityDays),
                    flags == null ? Set.of() : Set.copyOf(flags.stream().map(f -> parse(PrpsFlag.class, f)).toList()),
                    notices == null ? List.of() : notices.stream()
                            .map(n -> n == null ? null : new PrpsNtc(n.lang(), n.title(), n.text())).toList());
        }

        static ContentDto of(PrpsVerContent c) {
            return new ContentDto(c.name(), c.description(), c.dCats().stream().map(DCatDto::of).toList(),
                    c.recipients(), c.transfers(), c.retentionDays(), c.requestedValidityDays(), c.maxValidityDays(),
                    c.flags().stream().map(Enum::name).sorted().toList(), c.notices().stream().map(NtcDto::of).toList());
        }
    }

    record CreateDto(String code, ContentDto content) {
    }

    record ChangeDto(String kind, List<String> material, List<String> minor) {

        static ChangeDto of(PrpsChange c) {
            return new ChangeDto(c.kind().name(), c.material(), c.minor());
        }
    }

    record VerDto(String id, int verNo, String sts, ContentDto content, ChangeDto change, Instant createdAt,
                  Instant publishedAt) {

        static VerDto of(PrpsVer v) {
            return new VerDto(v.id(), v.verNo(), v.sts().name(), ContentDto.of(v.content()),
                    v.change() == null ? null : ChangeDto.of(v.change()), v.createdAt(), v.publishedAt());
        }
    }

    /** A purpose with every version, oldest first; {@code currentVer} is what requests are made from. */
    record DefDto(String id, String code, Integer currentVer, Integer draftVer, Instant createdAt,
                  List<VerDto> versions) {

        static DefDto of(PrpsDef def) {
            return new DefDto(def.id(), def.code(), def.current().map(PrpsVer::verNo).orElse(null),
                    def.draft().map(PrpsVer::verNo).orElse(null), def.createdAt(),
                    def.versions().stream().map(VerDto::of).toList());
        }
    }

    /** A purpose in a list: its current version in full, so a request can be raised from it, and any draft's number. */
    record SummaryDto(String id, String code, Integer currentVer, Integer draftVer, ContentDto current,
                      ContentDto draft, Instant updatedAt) {

        static SummaryDto of(PrpsDef def) {
            PrpsVer cur = def.current().orElse(null);
            PrpsVer draft = def.draft().orElse(null);
            Instant updated = def.versions().stream()
                    .map(v -> v.publishedAt() != null ? v.publishedAt() : v.createdAt())
                    .max(Instant::compareTo).orElse(def.createdAt());
            return new SummaryDto(def.id(), def.code(), cur == null ? null : cur.verNo(),
                    draft == null ? null : draft.verNo(), cur == null ? null : ContentDto.of(cur.content()),
                    draft == null ? null : ContentDto.of(draft.content()), updated);
        }
    }

    private static int orZero(Integer value) {
        return value == null ? 0 : value;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value) {
        try {
            return Enum.valueOf(type, value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new DomRuleViolation(400, "invalid-purpose", "'" + value + "' isn't one of "
                    + java.util.Arrays.toString(type.getEnumConstants()));
        }
    }
}
