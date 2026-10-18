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
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.FormFieldPart;
import org.springframework.http.codec.multipart.Part;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.techeazyhq.cm.cnscmd.app.CnsReqBatchQueries;
import com.techeazyhq.cm.cnscmd.app.SubmitCnsReqBatchHandler;
import com.techeazyhq.cm.cnscmd.dom.CnsReqBatch;
import com.techeazyhq.cm.cnscmd.dom.DCat;
import com.techeazyhq.cm.cnscmd.dom.ExtDpRefTyp;
import com.techeazyhq.cm.seckit.AuthCtx;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Batches of consent requests (ADR-036), for the fiduciary's people and its systems alike.
 * <ul>
 *   <li>{@code POST /api/cns-cmd/cns-req-batches}: JSON ({@code recipients} listed), or a multipart upload of a CSV
 *       or Excel file with the template as form fields. {@code ?dryRun=true} checks the list and reports, sending
 *       nothing. A stored batch answers 202; it is sent in the background.</li>
 *   <li>{@code GET .../cns-req-batches}, {@code .../{id}}, {@code .../{id}/rows} and {@code .../{id}/report.csv}.</li>
 * </ul>
 */
public class CnsReqBatchHttpHandler {

    private final SubmitCnsReqBatchHandler submit;
    private final CnsReqBatchQueries queries;

    public CnsReqBatchHttpHandler(SubmitCnsReqBatchHandler submit, CnsReqBatchQueries queries) {
        this.submit = submit;
        this.queries = queries;
    }

    public Mono<ServerResponse> create(ServerRequest request) {
        boolean dryRun = request.queryParam("dryRun").map(Boolean::parseBoolean).orElse(false);
        boolean multipart = request.headers().contentType()
                .map(MediaType.MULTIPART_FORM_DATA::isCompatibleWith).orElse(false);
        return asDf(request, true, ctx -> (multipart ? fromUpload(request, ctx, dryRun) : fromJson(request, ctx, dryRun))
                .flatMap(submit::handle)
                .flatMap(result -> ServerResponse.status(result.stored() ? HttpStatus.ACCEPTED : HttpStatus.OK)
                        .bodyValue(SubmitRespDto.of(result))));
    }

    public Mono<ServerResponse> list(ServerRequest request) {
        int limit = intParam(request, "limit", 50);
        int offset = intParam(request, "offset", 0);
        return asDf(request, false, ctx -> queries.list(ctx.dfId(), limit, offset).map(BatchDto::of).collectList()
                .flatMap(batches -> ServerResponse.ok().bodyValue(batches)));
    }

    public Mono<ServerResponse> get(ServerRequest request) {
        return asDf(request, false, ctx -> queries.get(ctx.dfId(), request.pathVariable("id"))
                .flatMap(batch -> ServerResponse.ok().bodyValue(BatchDto.of(batch))));
    }

    public Mono<ServerResponse> rows(ServerRequest request) {
        CnsReqBatch.Outcome outcome = request.queryParam("outcome").filter(s -> !s.isBlank())
                .map(s -> parse(CnsReqBatch.Outcome.class, s, "outcome")).orElse(null);
        int limit = intParam(request, "limit", 100);
        int offset = intParam(request, "offset", 0);
        return asDf(request, false, ctx -> queries.rows(ctx.dfId(), request.pathVariable("id"), outcome, limit, offset)
                .map(RowDto::of).collectList()
                .flatMap(rows -> ServerResponse.ok().bodyValue(rows)));
    }

    /** Every row with its outcome, as a CSV the fiduciary can open beside its own list. */
    public Mono<ServerResponse> report(ServerRequest request) {
        String id = request.pathVariable("id");
        return asDf(request, false, ctx -> queries.get(ctx.dfId(), id).flatMap(batch -> {
            Flux<String> lines = Flux.concat(Flux.just("row,contact,ref,outcome,consent_request_id,problem\n"),
                    queries.rows(ctx.dfId(), id, null, CnsReqBatch.MAX_ROWS, 0).map(r -> String.join(",",
                            String.valueOf(r.rowNo()), csv(r.contact()), csv(r.ref()), r.outcome().name(),
                            csv(r.cnsReqId()), csv(r.problem())) + "\n"));
            return ServerResponse.ok()
                    .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            ContentDisposition.attachment().filename("batch-" + id + ".csv").build().toString())
                    .body(lines, String.class);
        }));
    }

    private Mono<SubmitCnsReqBatchHandler.Cmd> fromJson(ServerRequest request, AuthCtx ctx, boolean dryRun) {
        return request.bodyToMono(BatchReqDto.class)
                .switchIfEmpty(Mono.error(new DomRuleViolation(400, "invalid-batch", "The request body is empty")))
                .map(body -> {
                    List<CnsReqBatch.Input> inputs = body.recipients() == null ? List.of()
                            : body.recipients().stream().map(RecipientDto::toInput).toList();
                    return new SubmitCnsReqBatchHandler.Cmd(ctx.dfId(), ctx.subject(), body.prpsCode(),
                            body.dCatCodes() == null ? Set.of() : Set.copyOf(body.dCatCodes()), body.ntcTitle(),
                            body.ntcText(), CnsReqBatch.Source.API, null, inputs, dryRun);
                });
    }

    private Mono<SubmitCnsReqBatchHandler.Cmd> fromUpload(ServerRequest request, AuthCtx ctx, boolean dryRun) {
        return request.multipartData().flatMap(parts -> {
            Part file = parts.getFirst("file");
            if (!(file instanceof FilePart filePart)) {
                return Mono.error(new DomRuleViolation(400, "invalid-batch", "Attach the list as the 'file' part"));
            }
            CnsReqBatch.Source source;
            try {
                source = RecipientListReader.sourceOf(filePart.filename(),
                        filePart.headers().getContentType() == null ? null : filePart.headers().getContentType().toString());
            } catch (IllegalArgumentException e) {
                return Mono.error(new DomRuleViolation(415, "unsupported-list", e.getMessage()));
            }
            return DataBufferUtils.join(filePart.content(), (int) RecipientListReader.MAX_FILE_BYTES + 1)
                    .onErrorMap(org.springframework.core.io.buffer.DataBufferLimitException.class,
                            e -> new DomRuleViolation(413, "list-too-large", "The file is larger than 5 MB; split the list"))
                    .map(buffer -> {
                        byte[] bytes = new byte[buffer.readableByteCount()];
                        buffer.read(bytes);
                        DataBufferUtils.release(buffer);
                        List<CnsReqBatch.Input> inputs;
                        try {
                            inputs = RecipientListReader.read(source, bytes);
                        } catch (IllegalArgumentException e) {
                            throw new DomRuleViolation(400, "invalid-list", e.getMessage());
                        }
                        return new SubmitCnsReqBatchHandler.Cmd(ctx.dfId(), ctx.subject(), field(parts, "prpsCode"),
                                categories(field(parts, "dCatCodes")), field(parts, "ntcTitle"), field(parts, "ntcText"),
                                source, filePart.filename(), inputs, dryRun);
                    });
        });
    }

    /** Fiduciary callers only; sending needs a role that raises requests (ADR-033), reading any of its people. */
    private Mono<ServerResponse> asDf(ServerRequest request, boolean sends, Function<AuthCtx, Mono<ServerResponse>> action) {
        return CnsReqHandler.authCtx(request)
                .filter(AuthCtx::isDf)
                .flatMap(ctx -> sends && !ctx.mayRaiseCnsReqs()
                        ? Mono.<ServerResponse>error(new DomRuleViolation(403, "not-permitted",
                                "Your role at this fiduciary doesn't raise consent requests"))
                        : action.apply(ctx))
                .switchIfEmpty(Mono.defer(() -> ServerResponse.status(HttpStatus.FORBIDDEN).build()));
    }

    private static String field(MultiValueMap<String, Part> parts, String name) {
        Part part = parts.getFirst(name);
        return part instanceof FormFieldPart f ? f.value() : null;
    }

    private static Set<String> categories(String commaSeparated) {
        if (commaSeparated == null) {
            return Set.of();
        }
        return new LinkedHashSet<>(Arrays.stream(commaSeparated.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList());
    }

    private static int intParam(ServerRequest request, String name, int fallback) {
        try {
            return request.queryParam(name).map(Integer::parseInt).orElse(fallback);
        } catch (NumberFormatException e) {
            throw new DomRuleViolation(400, "invalid-parameter", name + " must be a number");
        }
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value, String name) {
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new DomRuleViolation(400, "invalid-parameter", name + " must be one of "
                    + Arrays.toString(type.getEnumConstants()));
        }
    }

    /** A CSV cell, quoted when needed; a leading = + - @ is defused so a spreadsheet never runs it as a formula. */
    static String csv(String value) {
        if (value == null) {
            return "";
        }
        String v = !value.isEmpty() && "=+-@".indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
        return v.contains(",") || v.contains("\"") || v.contains("\n") ? "\"" + v.replace("\"", "\"\"") + "\"" : v;
    }

    /** A recipient in a JSON batch: {@code contact} (either kind), or {@code email} or {@code phone}; {@code ref} optional. */
    record RecipientDto(String contact, String email, String phone, String ref) {

        CnsReqBatch.Input toInput() {
            if (email != null && !email.isBlank()) {
                return new CnsReqBatch.Input(email, ref, ExtDpRefTyp.EMAIL);
            }
            if (phone != null && !phone.isBlank()) {
                return new CnsReqBatch.Input(phone, ref, ExtDpRefTyp.PHONE);
            }
            return new CnsReqBatch.Input(contact, ref, null);
        }
    }

    record BatchReqDto(String prpsCode, List<String> dCatCodes, String ntcTitle, String ntcText,
                       List<RecipientDto> recipients) {
    }

    record BatchDto(String id, String sts, String source, String fileName, String prpsCode, List<String> dCatCodes,
                    String ntcTitle, int total, int valid, int invalid, int duplicate, int sent, int failed, int pending,
                    Instant createdAt, Instant finishedAt) {

        static BatchDto of(CnsReqBatch b) {
            return new BatchDto(b.id(), b.sts().name(), b.source().name(), b.fileName(), b.template().prps().code(),
                    b.template().scp().dCats().stream().map(DCat::code).sorted().toList(), b.template().ntcTitle(),
                    b.total(), b.valid(), b.invalid(), b.duplicate(), b.sent(), b.failed(),
                    Math.max(0, b.valid() - b.sent() - b.failed()), b.createdAt(), b.finishedAt());
        }
    }

    record RowDto(int rowNo, String contact, String ref, String outcome, String cnsReqId, String problem) {

        static RowDto of(CnsReqBatch.Row r) {
            return new RowDto(r.rowNo(), r.contact(), r.ref(), r.outcome().name(), r.cnsReqId(), r.problem());
        }
    }

    /** {@code stored} false for a dry run; {@code available}: requests left this month (after this batch, if stored). */
    record SubmitRespDto(boolean stored, BatchDto batch, List<RowDto> problems, long available) {

        static SubmitRespDto of(SubmitCnsReqBatchHandler.Result r) {
            return new SubmitRespDto(r.stored(), BatchDto.of(r.batch()), r.problems().stream().map(RowDto::of).toList(),
                    r.available());
        }
    }
}
