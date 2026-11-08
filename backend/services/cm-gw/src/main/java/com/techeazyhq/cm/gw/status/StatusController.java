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

package com.techeazyhq.cm.gw.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import com.techeazyhq.cm.gw.status.StatusMath.Severity;
import com.techeazyhq.cm.webkit.Problems;

import reactor.core.publisher.Mono;

/**
 * {@code GET /api/status}: the platform's public status (ADR-067), for anyone, with no sign-in: a status page is read when
 * you cannot sign in. {@code POST /api/status/notices} and {@code .../{id}/resolve} are platform staff posting what is wrong or
 * planned, and saying it is over (the gateway's security allows only staff).
 */
@RestController
public class StatusController {

    private final StatusService status;

    public StatusController(StatusService status) {
        this.status = status;
    }

    @GetMapping("/api/status")
    public Mono<ResponseEntity<StatusService.Report>> get() {
        return status.report().map(r -> ResponseEntity.ok().header("Cache-Control", "public, max-age=15").body(r));
    }

    public record NoticeReq(String title, String body, String severity, List<String> componentIds) {
    }

    @PostMapping("/api/status/notices")
    public Mono<ResponseEntity<Object>> post(@RequestBody NoticeReq req, Authentication auth, ServerWebExchange exchange) {
        String title = req.title() == null ? "" : req.title().trim();
        String body = req.body() == null ? "" : req.body().trim();
        Severity severity;
        try {
            severity = Severity.valueOf(String.valueOf(req.severity()).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return invalid(exchange, "The severity is info, maintenance or incident");
        }
        List<String> components = req.componentIds() == null ? List.of() : req.componentIds();
        if (title.isEmpty() || title.length() > 120) {
            return invalid(exchange, "A title of 1 to 120 characters is needed");
        }
        if (body.length() > 2000) {
            return invalid(exchange, "The text is at most 2000 characters");
        }
        if (!status.knownIds().containsAll(components)) {
            return invalid(exchange, "Name only components the status page shows");
        }
        String by = auth instanceof JwtAuthenticationToken t ? ((Jwt) t.getPrincipal()).getSubject() : "staff";
        return status.post(title, body, severity, components, by).map(n -> ResponseEntity.status(HttpStatus.CREATED).<Object>body(n));
    }

    @PostMapping("/api/status/notices/{id}/resolve")
    public Mono<ResponseEntity<Object>> resolve(@PathVariable String id, ServerWebExchange exchange) {
        return status.resolve(id).map(n -> ResponseEntity.ok().<Object>body(n))
                .switchIfEmpty(Mono.fromSupplier(() -> problem(exchange, "invalid-request", HttpStatus.BAD_REQUEST, "There is no open notice " + id)));
    }

    private static Mono<ResponseEntity<Object>> invalid(ServerWebExchange exchange, String detail) {
        return Mono.just(problem(exchange, "invalid-request", HttpStatus.BAD_REQUEST, detail));
    }

    private static ResponseEntity<Object> problem(ServerWebExchange exchange, String code, HttpStatus status, String detail) {
        byte[] body = Problems.json(code, status.value(), detail, Problems.correlationId(exchange));
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(new String(body, StandardCharsets.UTF_8));
    }
}
