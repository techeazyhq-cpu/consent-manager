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
import java.util.function.Function;

import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.techeazyhq.cm.cnscmd.app.AiDraftingSettings;
import com.techeazyhq.cm.cnscmd.dom.AiDraftingConfig;
import com.techeazyhq.cm.cnscmd.dom.AiProvider;
import com.techeazyhq.cm.seckit.AuthCtx;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Mono;

/**
 * Which model drafts purposes, set by platform staff in CM Admin (ADR-113). The key is write-only: it is accepted on {@code PUT} and {@code POST test}
 * and never returned; the setting says only whether one is stored.
 * <ul>
 *   <li>{@code GET /api/cns-cmd/admin/ai-drafting}: the setting and what each provider needs. Platform staff.</li>
 *   <li>{@code PUT /api/cns-cmd/admin/ai-drafting}: saves it and uses it from now on. Administrators only.</li>
 *   <li>{@code POST /api/cns-cmd/admin/ai-drafting/test}: a one-line ping with the setting as submitted. Administrators only.</li>
 * </ul>
 */
public class AiDraftingAdminHandler {

    private final AiDraftingSettings settings;

    public AiDraftingAdminHandler(AiDraftingSettings settings) {
        this.settings = settings;
    }

    public Mono<ServerResponse> get(ServerRequest request) {
        return asStaff(request, false, ctx -> ServerResponse.ok().bodyValue(ViewDto.of(settings.view())));
    }

    public Mono<ServerResponse> put(ServerRequest request) {
        return asStaff(request, true, ctx -> request.bodyToMono(UpdateDto.class)
                .switchIfEmpty(Mono.error(() -> new DomRuleViolation(400, "invalid-ai-config", "Send the setting")))
                .flatMap(body -> settings.update(ctx.subject(), body.toDomain()))
                .flatMap(view -> ServerResponse.ok().bodyValue(ViewDto.of(view))));
    }

    public Mono<ServerResponse> test(ServerRequest request) {
        return asStaff(request, true, ctx -> request.bodyToMono(UpdateDto.class)
                .switchIfEmpty(Mono.error(() -> new DomRuleViolation(400, "invalid-ai-config", "Send the setting")))
                .flatMap(body -> settings.test(ctx.subject(), body.toDomain()))
                .flatMap(r -> ServerResponse.ok().bodyValue(r)));
    }

    /** Platform staff may look; changing the model is for administrators (the gateway says the same; this is the second check). */
    static Mono<ServerResponse> asStaff(ServerRequest request, boolean changes, Function<AuthCtx, Mono<ServerResponse>> action) {
        return CnsReqHandler.authCtx(request)
                .filter(AuthCtx::isPlatformStaff)
                .flatMap(ctx -> changes && !ctx.isCmAdmin()
                        ? Mono.<ServerResponse>error(new DomRuleViolation(403, "not-permitted", "Only a platform administrator changes the drafting model"))
                        : action.apply(ctx))
                .switchIfEmpty(Mono.defer(() -> ServerResponse.status(HttpStatus.FORBIDDEN).build()));
    }

    // ---- wire formats

    /** {@code apiKey} is only ever on the way in. */
    record UpdateDto(Boolean enabled, String provider, String endpoint, String model, String apiKey, Boolean clearKey, Integer timeoutSeconds,
                     Integer maxTokens, Boolean thirdPartyAck) {

        AiDraftingSettings.Update toDomain() {
            AiProvider p;
            try {
                p = provider == null ? null : AiProvider.valueOf(provider.strip().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new DomRuleViolation(400, "invalid-ai-config", "Unknown provider");
            }
            return new AiDraftingSettings.Update(Boolean.TRUE.equals(enabled), p, endpoint, model, apiKey, Boolean.TRUE.equals(clearKey), timeoutSeconds,
                    maxTokens, Boolean.TRUE.equals(thirdPartyAck));
        }
    }

    record ConfigDto(boolean enabled, String provider, String endpoint, String model, boolean apiKeySet, int timeoutSeconds, int maxTokens,
                     boolean thirdPartyAck, boolean leavesPlatform, String updatedBy, Instant updatedAt) {

        static ConfigDto of(AiDraftingConfig c) {
            return new ConfigDto(c.enabled(), c.provider().name(), c.provider().endpointEditable() ? c.endpoint() : c.provider().defaultEndpoint(),
                    c.model(), c.hasKey(), c.timeoutSeconds(), c.maxTokens(), c.thirdPartyAck(), c.leavesPlatform(), c.updatedBy(), c.updatedAt());
        }
    }

    record ViewDto(ConfigDto config, boolean fromProperties, List<AiDraftingSettings.ProviderInfo> providers) {

        static ViewDto of(AiDraftingSettings.View v) {
            return new ViewDto(ConfigDto.of(v.config()), v.fromProperties(), v.providers());
        }
    }
}
