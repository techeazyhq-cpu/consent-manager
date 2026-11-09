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

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.techeazyhq.cm.cnsqry.app.DfInsightsQryHandler;
import com.techeazyhq.cm.seckit.AuthCtx;

import reactor.core.publisher.Mono;

/**
 * {@code GET /api/cns-qry/insights?days=30} (ADR-049): the caller's own fiduciary's outcomes and times to decide, for
 * every member and its API clients. The fiduciary is the token's; anyone else gets 403.
 */
public class DfInsightsHandler {

    private final DfInsightsQryHandler insights;

    public DfInsightsHandler(DfInsightsQryHandler insights) {
        this.insights = insights;
    }

    public Mono<ServerResponse> insights(ServerRequest request) {
        Query q = Query.of(request);
        if (q == null) {
            return ServerResponse.badRequest().build();
        }
        return request.principal().cast(JwtAuthenticationToken.class)
                .map(token -> AuthCtx.from((Jwt) token.getPrincipal()))
                .filter(AuthCtx::isDf)
                .flatMap(ctx -> q.run(insights, ctx.dfId(), q.compare()))
                .flatMap(result -> ServerResponse.ok().bodyValue(result))
                // a month out of range is the caller's mistake, like a bad zone
                .onErrorResume(IllegalArgumentException.class, e -> ServerResponse.badRequest().build())
                .switchIfEmpty(Mono.defer(() -> ServerResponse.status(HttpStatus.FORBIDDEN).build()));
    }

    /**
     * {@code GET /api/cns-qry/insights/export?days=30&tz=Asia/Kolkata&view=days|purposes}: the same figures as a CSV file, the
     * day-by-day series or the purposes (ADR-078).
     */
    public Mono<ServerResponse> export(ServerRequest request) {
        Query q = Query.of(request);
        String view = request.queryParam("view").orElse("days");
        if (q == null || !(view.equals("days") || view.equals("purposes"))) {
            return ServerResponse.badRequest().build();
        }
        return request.principal().cast(JwtAuthenticationToken.class)
                .map(token -> AuthCtx.from((Jwt) token.getPrincipal()))
                .filter(AuthCtx::isDf)
                .flatMap(ctx -> q.run(insights, ctx.dfId(), false))
                .flatMap(result -> ServerResponse.ok()
                        .contentType(new org.springframework.http.MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                        .header("Content-Disposition", "attachment; filename=\"consent-insights-" + view + ".csv\"")
                        .bodyValue(view.equals("days") ? InsightsCsv.days(result) : InsightsCsv.purposes(result)))
                .onErrorResume(IllegalArgumentException.class, e -> ServerResponse.badRequest().build())
                .switchIfEmpty(Mono.defer(() -> ServerResponse.status(HttpStatus.FORBIDDEN).build()));
    }

    /**
     * The window, time zone and comparison a request asks for; null when any of them is not valid. {@code month} (yyyy-MM, ADR-088),
     * {@code quarter} (yyyy-Qn) or {@code year} (yyyy) (ADR-102) asks for a calendar month, quarter or year in the zone and takes the
     * place of {@code days}; asking for more than one of them is not valid.
     */
    record Query(int days, java.time.YearMonth month, int[] quarter, Integer year, java.time.ZoneId zone, boolean compare) {
        static Query of(ServerRequest request) {
            try {
                int days = request.queryParam("days").map(Integer::parseInt).orElse(DfInsightsQryHandler.DEFAULT_DAYS);
                java.time.YearMonth month = request.queryParam("month").map(java.time.YearMonth::parse).orElse(null);
                int[] quarter = request.queryParam("quarter").map(Query::parseQuarter).orElse(null);
                Integer year = request.queryParam("year").map(y -> {
                    if (!y.matches("\\d{4}")) {
                        throw new java.time.DateTimeException("not a year");
                    }
                    return Integer.valueOf(y);
                }).orElse(null);
                if ((month != null ? 1 : 0) + (quarter != null ? 1 : 0) + (year != null ? 1 : 0) > 1) {
                    return null;
                }
                java.time.ZoneId zone = request.queryParam("tz").map(java.time.ZoneId::of).orElse(java.time.ZoneOffset.UTC);
                boolean compare = request.queryParam("compare").map(Boolean::parseBoolean).orElse(false);
                return new Query(days, month, quarter, year, zone, compare);
            } catch (NumberFormatException | java.time.DateTimeException e) {
                return null;
            }
        }

        /** {@code yyyy-Qn}, such as 2026-Q3, as {year, quarter}. */
        private static int[] parseQuarter(String text) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{4})-Q([1-4])").matcher(text);
            if (!m.matches()) {
                throw new java.time.DateTimeException("not a quarter");
            }
            return new int[] {Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))};
        }

        Mono<com.techeazyhq.cm.cnsqry.dom.DfInsights> run(DfInsightsQryHandler insights, String dfId, boolean withComparison) {
            if (quarter != null) {
                return insights.handleQuarter(dfId, quarter[0], quarter[1], zone, withComparison);
            }
            if (year != null) {
                return insights.handleYear(dfId, year, zone, withComparison);
            }
            if (month == null) {
                return insights.handle(dfId, days, zone, withComparison);
            }
            return insights.handleMonth(dfId, month, zone, withComparison);
        }
    }
}
