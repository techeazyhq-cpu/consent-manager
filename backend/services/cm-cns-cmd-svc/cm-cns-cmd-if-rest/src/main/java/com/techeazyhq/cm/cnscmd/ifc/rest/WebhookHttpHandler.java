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

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.techeazyhq.cm.cnscmd.app.Webhooks;
import com.techeazyhq.cm.cnscmd.dom.WhkDelivery;
import com.techeazyhq.cm.cnscmd.dom.WhkEndpoint;
import com.techeazyhq.cm.cnscmd.dom.WhkMessage;
import com.techeazyhq.cm.seckit.AuthCtx;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Mono;

/**
 * A fiduciary's webhooks (ADR-047), for its people and its systems: endpoints under {@code /api/cns-cmd/webhooks/
 * endpoints}, deliveries under {@code .../webhooks/deliveries}. Reading is open to all its people; changes to Owners,
 * Administrators, Developers and API clients (ADR-033). A signing secret appears in a response once: when the endpoint
 * is made, or its secret rotated.
 */
public class WebhookHttpHandler {

    private final Webhooks webhooks;

    public WebhookHttpHandler(Webhooks webhooks) {
        this.webhooks = webhooks;
    }

    public Mono<ServerResponse> types(ServerRequest request) {
        return asDf(request, false, ctx -> ServerResponse.ok().bodyValue(WhkMessage.TYPES));
    }

    public Mono<ServerResponse> list(ServerRequest request) {
        return asDf(request, false, ctx -> webhooks.endpoints(ctx.dfId()).map(EndpointDto::of).collectList()
                .flatMap(list -> ServerResponse.ok().bodyValue(list)));
    }

    public Mono<ServerResponse> get(ServerRequest request) {
        return asDf(request, false, ctx -> webhooks.endpoint(ctx.dfId(), request.pathVariable("id"))
                .flatMap(e -> ServerResponse.ok().bodyValue(EndpointDto.of(e))));
    }

    public Mono<ServerResponse> create(ServerRequest request) {
        return asDf(request, true, ctx -> request.bodyToMono(CreateDto.class)
                .switchIfEmpty(Mono.error(() -> new DomRuleViolation(400, "invalid-webhook", "The request body is empty")))
                .flatMap(b -> webhooks.create(ctx.dfId(), b.url(), b.description(),
                        b.types() == null ? Set.of() : Set.copyOf(b.types())))
                .flatMap(w -> ServerResponse.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                        .bodyValue(WithSecretDto.of(w))));
    }

    public Mono<ServerResponse> rotate(ServerRequest request) {
        return asDf(request, true, ctx -> webhooks.rotateSecret(ctx.dfId(), request.pathVariable("id"))
                .flatMap(w -> ServerResponse.ok().cacheControl(CacheControl.noStore()).bodyValue(WithSecretDto.of(w))));
    }

    public Mono<ServerResponse> enable(ServerRequest request) {
        return active(request, true);
    }

    public Mono<ServerResponse> disable(ServerRequest request) {
        return active(request, false);
    }

    private Mono<ServerResponse> active(ServerRequest request, boolean active) {
        return asDf(request, true, ctx -> webhooks.setActive(ctx.dfId(), request.pathVariable("id"), active)
                .flatMap(e -> ServerResponse.ok().bodyValue(EndpointDto.of(e))));
    }

    public Mono<ServerResponse> delete(ServerRequest request) {
        return asDf(request, true, ctx -> webhooks.delete(ctx.dfId(), request.pathVariable("id"))
                .then(ServerResponse.noContent().build()));
    }

    public Mono<ServerResponse> test(ServerRequest request) {
        return asDf(request, true, ctx -> webhooks.sendTest(ctx.dfId(), request.pathVariable("id"))
                .flatMap(d -> ServerResponse.status(HttpStatus.ACCEPTED).bodyValue(DeliveryDto.of(d, false))));
    }

    public Mono<ServerResponse> deliveries(ServerRequest request) {
        WhkDelivery.Sts sts = request.queryParam("sts").filter(s -> !s.isBlank()).map(s -> {
            try {
                return WhkDelivery.Sts.valueOf(s.strip().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new DomRuleViolation(400, "invalid-parameter", "sts is PENDING, DELIVERED or FAILED");
            }
        }).orElse(null);
        int limit;
        try {
            limit = request.queryParam("limit").map(Integer::parseInt).orElse(50);
        } catch (NumberFormatException e) {
            throw new DomRuleViolation(400, "invalid-parameter", "limit must be a number");
        }
        String endpointId = request.queryParam("endpointId").filter(s -> !s.isBlank()).orElse(null);
        return asDf(request, false, ctx -> webhooks.deliveries(ctx.dfId(), endpointId, sts, limit)
                .map(d -> DeliveryDto.of(d, false)).collectList()
                .flatMap(list -> ServerResponse.ok().bodyValue(list)));
    }

    public Mono<ServerResponse> delivery(ServerRequest request) {
        return asDf(request, false, ctx -> webhooks.delivery(ctx.dfId(), request.pathVariable("id"))
                .flatMap(v -> ServerResponse.ok().bodyValue(new DeliveryDetailDto(DeliveryDto.of(v.delivery(), true),
                        v.attempts()))));
    }

    public Mono<ServerResponse> replay(ServerRequest request) {
        return asDf(request, true, ctx -> webhooks.replay(ctx.dfId(), request.pathVariable("id"))
                .flatMap(d -> ServerResponse.status(HttpStatus.ACCEPTED).bodyValue(DeliveryDto.of(d, false))));
    }

    private static Mono<ServerResponse> asDf(ServerRequest request, boolean changes,
                                             Function<AuthCtx, Mono<ServerResponse>> action) {
        return CnsReqHandler.authCtx(request)
                .filter(AuthCtx::isDf)
                .flatMap(ctx -> changes && !ctx.mayManageWebhooks()
                        ? Mono.<ServerResponse>error(new DomRuleViolation(403, "not-permitted",
                                "Your role at this fiduciary doesn't manage webhooks"))
                        : action.apply(ctx))
                .switchIfEmpty(Mono.defer(() -> ServerResponse.status(HttpStatus.FORBIDDEN).build()));
    }

    record CreateDto(String url, String description, List<String> types) {
    }

    /** An endpoint, never its secret. {@code previousSecretUntil}: when a rotated-out secret stops being honoured. */
    record EndpointDto(String id, String url, String description, List<String> types, boolean active,
                       Instant createdAt, Instant previousSecretUntil) {

        static EndpointDto of(WhkEndpoint e) {
            Instant until = e.prevSecretUntil() != null && e.prevSecretUntil().isAfter(Instant.now())
                    ? e.prevSecretUntil() : null;
            return new EndpointDto(e.id(), e.url(), e.description(), e.types().stream().sorted().toList(), e.active(),
                    e.createdAt(), until);
        }
    }

    /** The endpoint and its signing secret: shown this once. */
    record WithSecretDto(EndpointDto endpoint, String secret) {

        static WithSecretDto of(Webhooks.WithSecret w) {
            return new WithSecretDto(EndpointDto.of(w.endpoint()), w.secret());
        }
    }

    record DeliveryDto(String id, String endpointId, String msgId, String type, String sts, int attempts,
                       Instant nextAttemptAt, Integer lastStatus, String lastError, Instant deliveredAt,
                       Instant createdAt, String payload) {

        static DeliveryDto of(WhkDelivery d, boolean withPayload) {
            return new DeliveryDto(d.id(), d.endpointId(), d.msgId(), d.type(), d.sts().name(), d.attempts(),
                    d.sts() == WhkDelivery.Sts.PENDING ? d.nextAttemptAt() : null, d.lastStatus(), d.lastError(),
                    d.deliveredAt(), d.createdAt(), withPayload ? d.payload() : null);
        }
    }

    record DeliveryDetailDto(DeliveryDto delivery, List<WhkDelivery.Attempt> attempts) {
    }
}
