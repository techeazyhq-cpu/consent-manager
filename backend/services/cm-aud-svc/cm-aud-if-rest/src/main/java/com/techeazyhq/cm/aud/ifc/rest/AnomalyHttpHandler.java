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
package com.techeazyhq.cm.aud.ifc.rest;

import java.time.Clock;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.techeazyhq.cm.aud.dom.AnomalyFinding;
import com.techeazyhq.cm.aud.dom.AnomalyPort;
import com.techeazyhq.cm.seckit.AuthCtx;
import com.techeazyhq.cm.webkit.Problems;

import reactor.core.publisher.Mono;

/**
 * {@code /api/aud/anomalies}: what the detector found (ADR-112), for platform administrators only. A fiduciary never sees that another was
 * flagged. A finding holds no principal's data; acknowledging one records who and when.
 */
public class AnomalyHttpHandler {

    private final AnomalyPort port;
    private final Clock clock;

    public AnomalyHttpHandler(AnomalyPort port, Clock clock) {
        this.port = port;
        this.clock = clock;
    }

    public record FindingDto(String id, String dfId, String signal, String windowStart, long observed, double expected, double score,
                             String detectedAt, String status, String acknowledgedBy, String acknowledgedAt) {
        static FindingDto of(AnomalyFinding f) {
            return new FindingDto(f.id().toString(), f.dfId(), f.signal(), f.windowStart().toString(), f.observed(),
                    Math.round(f.expected() * 100) / 100.0, Math.round(f.score() * 10) / 10.0, f.detectedAt().toString(), f.status(),
                    f.acknowledgedBy(), f.acknowledgedAt() == null ? null : f.acknowledgedAt().toString());
        }
    }

    public Mono<ServerResponse> list(ServerRequest request) {
        return asAdmin(request, who -> {
            String status = request.queryParam("status").map(String::toUpperCase).orElse(AnomalyFinding.OPEN);
            if (!status.equals(AnomalyFinding.OPEN) && !status.equals(AnomalyFinding.ACKNOWLEDGED) && !status.equals("ALL")) {
                return problem(400, Problems.body("invalid-request", 400, "status is OPEN, ACKNOWLEDGED or ALL", null));
            }
            int limit = request.queryParam("limit").map(v -> {
                try {
                    return Math.max(1, Math.min(200, Integer.parseInt(v)));
                } catch (NumberFormatException e) {
                    return 100;
                }
            }).orElse(100);
            return port.list(status.equals("ALL") ? null : status, limit).map(FindingDto::of).collectList()
                    .flatMap(list -> ServerResponse.ok().bodyValue(list));
        });
    }

    public Mono<ServerResponse> acknowledge(ServerRequest request) {
        return asAdmin(request, who -> {
            UUID id;
            try {
                id = UUID.fromString(request.pathVariable("id"));
            } catch (IllegalArgumentException e) {
                return problem(400, Problems.body("invalid-request", 400, "not a finding id", null));
            }
            return port.acknowledge(id, who, clock.instant()).flatMap(done -> done
                    ? ServerResponse.noContent().build()
                    : problem(404, Problems.body("anomaly-not-found", 404, "No open finding with that id", null)));
        });
    }

    /** Runs {@code then} with the administrator's id; anyone else is refused. */
    private Mono<ServerResponse> asAdmin(ServerRequest request, java.util.function.Function<String, Mono<ServerResponse>> then) {
        return request.principal().cast(JwtAuthenticationToken.class).flatMap(token -> {
            Jwt jwt = (Jwt) token.getPrincipal();
            return AuthCtx.from(jwt).isCmAdmin() ? then.apply(jwt.getSubject())
                    : ServerResponse.status(403).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                            .bodyValue(Problems.body("aud-forbidden", 403, null, Problems.correlationId(request.exchange())));
        });
    }

    private static Mono<ServerResponse> problem(int status, java.util.Map<String, Object> body) {
        return ServerResponse.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).bodyValue(body);
    }
}
