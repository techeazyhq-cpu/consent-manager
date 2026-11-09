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

package com.techeazyhq.cm.bil.ifc.rest;

import java.time.Instant;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.techeazyhq.cm.bil.app.InvoiceCmds;
import com.techeazyhq.cm.bil.app.InvoiceExport;
import com.techeazyhq.cm.bil.app.InvoiceQueries;
import com.techeazyhq.cm.bil.dom.Invoice;
import com.techeazyhq.cm.seckit.AuthCtx;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Mono;

/**
 * Invoices (ADR-079). A fiduciary reads its own: {@code GET /api/bil/me/invoices}, {@code .../{id}} and {@code .../{id}/html}
 * (a printable page). Platform staff read everyone's: {@code GET /api/bil/invoices?df=&month=yyyy-MM}, the accounting export
 * {@code .../invoices/export?month=}, and the plan prices; administrators run a month ({@code POST /api/bil/invoices/run?month=}),
 * mark an invoice paid, credit one, set a price and end a fiduciary's billing.
 */
public class BilInvoiceHttpHandler {

    private final InvoiceCmds cmds;
    private final InvoiceQueries queries;
    private final com.techeazyhq.cm.bil.dom.InvoiceRepoPort repo;

    public BilInvoiceHttpHandler(InvoiceCmds cmds, InvoiceQueries queries, com.techeazyhq.cm.bil.dom.InvoiceRepoPort repo) {
        this.cmds = cmds;
        this.queries = queries;
        this.repo = repo;
    }

    // ---- a fiduciary's own

    public Mono<ServerResponse> myInvoices(ServerRequest request) {
        return ownDf(request).flatMap(df -> queries.mine(df).map(Dto::of).collectList())
                .flatMap(list -> ServerResponse.ok().bodyValue(list));
    }

    public Mono<ServerResponse> myInvoice(ServerRequest request) {
        return ownDf(request).flatMap(df -> queries.mine(df, request.pathVariable("id")))
                .flatMap(i -> ServerResponse.ok().bodyValue(Dto.of(i)));
    }

    public Mono<ServerResponse> myInvoiceHtml(ServerRequest request) {
        return ownDf(request).flatMap(df -> queries.mine(df, request.pathVariable("id")))
                .flatMap(i -> ServerResponse.ok().contentType(MediaType.TEXT_HTML)
                        .header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'")
                        .bodyValue(InvoiceExport.html(i, null)));
    }

    // ---- platform staff

    public Mono<ServerResponse> all(ServerRequest request) {
        return month(request).flatMap(m -> queries.all(request.queryParam("df").filter(s -> !s.isBlank()).orElse(null),
                        m.orElse(null)).map(Dto::of).collectList())
                .flatMap(list -> ServerResponse.ok().bodyValue(list));
    }

    public Mono<ServerResponse> export(ServerRequest request) {
        return month(request).flatMap(m -> queries.all(null, m.orElse(null)).collectList())
                .flatMap(list -> ServerResponse.ok()
                        .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                        .header("Content-Disposition", "attachment; filename=\"invoices.csv\"")
                        .bodyValue(InvoiceExport.csv(list)));
    }

    public Mono<ServerResponse> prices(ServerRequest request) {
        return queries.prices().collectList().flatMap(list -> ServerResponse.ok().bodyValue(list));
    }

    public Mono<ServerResponse> setPrice(ServerRequest request) {
        return request.bodyToMono(PriceDto.class)
                .switchIfEmpty(Mono.error(() -> new DomRuleViolation(400, "invalid-price", "Give monthlyMinor")))
                .flatMap(b -> cmds.setPrice(request.pathVariable("name"), b.monthlyMinor()))
                .flatMap(p -> ServerResponse.ok().bodyValue(p));
    }

    public Mono<ServerResponse> run(ServerRequest request) {
        return month(request).flatMap(m -> m.isPresent() ? cmds.runFor(m.get()).map(Dto::of).collectList()
                        : Mono.<List<Dto>>error(new DomRuleViolation(400, "invalid-month", "Give month=yyyy-MM")))
                .flatMap(list -> ServerResponse.ok().bodyValue(list));
    }

    public Mono<ServerResponse> paid(ServerRequest request) {
        return request.bodyToMono(NoteDto.class).defaultIfEmpty(new NoteDto(null))
                .flatMap(b -> cmds.markPaid(request.pathVariable("id"), b.note()))
                .flatMap(i -> ServerResponse.ok().bodyValue(Dto.of(i)));
    }

    public Mono<ServerResponse> creditNote(ServerRequest request) {
        return request.bodyToMono(ReasonDto.class).defaultIfEmpty(new ReasonDto(null))
                .flatMap(b -> cmds.creditNote(request.pathVariable("id"), b.reason()))
                .flatMap(i -> ServerResponse.status(HttpStatus.CREATED).bodyValue(Dto.of(i)));
    }

    public Mono<ServerResponse> endBilling(ServerRequest request) {
        return repo.endBilling(request.pathVariable("id"), Instant.now()).then(ServerResponse.noContent().build());
    }

    /** A departure that was called off (ADR-109): the fiduciary is billed again from now. */
    public Mono<ServerResponse> resumeBilling(ServerRequest request) {
        return repo.resumeBilling(request.pathVariable("id"), Instant.now()).then(ServerResponse.noContent().build());
    }

    // ---- helpers

    private static Mono<java.util.Optional<YearMonth>> month(ServerRequest request) {
        try {
            return Mono.just(request.queryParam("month").filter(s -> !s.isBlank()).map(YearMonth::parse));
        } catch (DateTimeParseException e) {
            return Mono.error(new DomRuleViolation(400, "invalid-month", "The month is yyyy-MM, for example 2026-10"));
        }
    }

    private static Mono<String> ownDf(ServerRequest request) {
        return request.principal().cast(JwtAuthenticationToken.class)
                .map(token -> AuthCtx.from((Jwt) token.getPrincipal()))
                .filter(AuthCtx::isDf)
                .map(AuthCtx::dfId)
                .switchIfEmpty(Mono.error(() -> new DomRuleViolation(403, "not-a-fiduciary", "Only a fiduciary has invoices")));
    }

    record PriceDto(long monthlyMinor) { }

    record NoteDto(String note) { }

    record ReasonDto(String reason) { }

    record LineDto(int lineNo, String description, String plan, Instant from, Instant to, String days, long amountMinor) { }

    record Dto(String id, String number, String kind, String fiduciaryId, Instant periodFrom, Instant periodTo, String currency,
               long subtotalMinor, String taxLabel, int taxBps, long taxMinor, long totalMinor, String status, Instant issuedAt,
               Instant paidAt, String paidNote, String creditsInvoiceId, String reason, List<LineDto> lines) {
        static Dto of(Invoice i) {
            return new Dto(i.id(), i.number(), i.kind().name(), i.dfId(), i.periodFrom(), i.periodTo(), i.currency(), i.subtotalMinor(),
                    i.taxLabel(), i.taxBps(), i.taxMinor(), i.totalMinor(), i.sts().name(), i.issuedAt(), i.paidAt(), i.paidNote(),
                    i.creditsInvoiceId(), i.reason(),
                    i.lines().stream().map(l -> new LineDto(l.lineNo(), l.description(), l.planName(), l.from(), l.to(),
                            l.qtyDays().stripTrailingZeros().toPlainString(), l.amountMinor())).toList());
        }
    }
}
