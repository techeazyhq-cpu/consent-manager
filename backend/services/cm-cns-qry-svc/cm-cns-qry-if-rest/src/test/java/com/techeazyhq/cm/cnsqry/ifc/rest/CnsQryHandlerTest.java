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

package com.techeazyhq.cm.cnsqry.ifc.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.reactive.function.server.MockServerRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.techeazyhq.cm.cnsqry.app.GetCnsArtQryHandler;
import com.techeazyhq.cm.cnsqry.app.GetCnsHstQryHandler;
import com.techeazyhq.cm.cnsqry.dom.CnsArtRdMdl;
import com.techeazyhq.cm.cnsqry.dom.CnsArtRdMdlRepoPort;
import com.techeazyhq.cm.cnsqry.dom.CnsHstRdMdl;
import com.techeazyhq.cm.cnsqry.dom.CnsHstRdMdlRepoPort;
import com.techeazyhq.cm.cnsqry.dom.CnsLcySts;
import com.techeazyhq.cm.cnsqry.dom.DpDashboardRow;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** One fiduciary's request is invisible to another, by id as in the list (VAPT IDOR-01, -02, -05). */
class CnsQryHandlerTest {

    private static final CnsArtRdMdl ROW = new CnsArtRdMdl("req-1", null, "df-a", "dp-1", "ORDER_UPDATES",
            Set.of("EMAIL"), CnsLcySts.PENDING, null, null, Instant.EPOCH, Instant.EPOCH, "Order updates", "Notice");

    private final CnsQryHandler handler = new CnsQryHandler(new GetCnsArtQryHandler(new OneRow()),
            new GetCnsHstQryHandler(new NoHistory()), null, null);

    @Test
    void itsFiduciaryReadsTheRequestAndItsHistory() {
        assertEquals(HttpStatus.OK, status(handler.getCnsArt(as(fiduciary("df-a")))));
        assertEquals(HttpStatus.OK, status(handler.getCnsHst(as(fiduciary("df-a")))));
    }

    @Test
    void anotherFiduciaryFindsNothing() {
        assertEquals(HttpStatus.NOT_FOUND, status(handler.getCnsArt(as(fiduciary("df-b")))));
        assertEquals(HttpStatus.NOT_FOUND, status(handler.getCnsHst(as(fiduciary("df-b")))));
    }

    @Test
    void itsDataPrincipalReadsItAndNoOtherDoes() {
        assertEquals(HttpStatus.OK, status(handler.getCnsArt(as(principal("dp-1")))));
        assertEquals(HttpStatus.NOT_FOUND, status(handler.getCnsArt(as(principal("dp-2")))));
    }

    @Test
    void staffUseTheAdminViewsNotThisOne() {
        Jwt staff = jwt("cm-staff", "admin-1", Map.of("roles", List.of("CM_ADMIN")));
        assertEquals(HttpStatus.NOT_FOUND, status(handler.getCnsArt(as(staff))));
    }

    private static Jwt fiduciary(String dfId) {
        return jwt("cm-df", "service-account-df-api-" + dfId, Map.of("df_id", dfId));
    }

    private static Jwt principal(String dpId) {
        return jwt("cm-dp", dpId, Map.of("roles", List.of("DP")));
    }

    private static Jwt jwt(String realm, String subject, Map<String, Object> claims) {
        return Jwt.withTokenValue("t").header("alg", "RS256").issuer("http://localhost:8080/realms/" + realm)
                .subject(subject).claims(c -> c.putAll(claims)).build();
    }

    private static MockServerRequest as(Jwt jwt) {
        return MockServerRequest.builder().pathVariable("id", "req-1").principal(new JwtAuthenticationToken(jwt))
                .build();
    }

    private static HttpStatus status(Mono<ServerResponse> response) {
        return HttpStatus.valueOf(response.block().statusCode().value());
    }

    private static final class OneRow implements CnsArtRdMdlRepoPort {
        @Override
        public Mono<CnsArtRdMdl> findByCnsReqId(String cnsReqId) {
            return ROW.cnsReqId().equals(cnsReqId) ? Mono.just(ROW) : Mono.empty();
        }

        @Override
        public Mono<CnsArtRdMdl> findByCnsArtId(String cnsArtId) {
            return Mono.empty();
        }

        @Override
        public Mono<CnsArtRdMdl> upsert(CnsArtRdMdl row) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Flux<CnsArtRdMdl> findByDpId(String dpId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Flux<CnsArtRdMdl> findByDfId(String dfId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Mono<Map<CnsLcySts, Long>> countByStsForDfId(String dfId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Flux<CnsArtRdMdl> pageByDfId(String dfId, CnsLcySts sts, int limit, long offset) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Mono<Void> linkDp(String recordId, String recordType, String newDpId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Flux<DpDashboardRow> dashboardFor(String dpId) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class NoHistory implements CnsHstRdMdlRepoPort {
        @Override
        public Mono<Void> append(CnsHstRdMdl row) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Flux<CnsHstRdMdl> findByCnsReqId(String cnsReqId) {
            return Flux.empty();
        }
    }
}
