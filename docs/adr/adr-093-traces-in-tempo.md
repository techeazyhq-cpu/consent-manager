# ADR-093: Traces in Tempo, off until asked for

- **Status:** Accepted
- **Date:** 2026-11-09
- **Issue:** #98
- **Builds on:** [ADR-070](adr-070-metrics-and-dashboards.md), [ADR-091](adr-091-logs-in-loki.md)

## Context

A slow or failing request crosses the gateway and one or two services. The metrics say which service is slow, the logs say what one
service did, and nothing ties one request's steps across services together. ADR-070 left traces out deliberately: they cost CPU
(ADR-052, the vapt-and-performance measurements) and nothing was reading them.

## Decision

1. **Every service and the gateway carry the OpenTelemetry bridge of Micrometer Tracing** and the OTLP exporter, so a request's trace id
   travels in the standard `traceparent` header on every call between them, and each service records a span for its part.
2. **Nothing is sampled by default:** `management.tracing.sampling.probability` is `${CM_TRACING_SAMPLING:0}`. With 0 no span is
   recorded or sent, so the cost stays that of an id passed along. Where Tempo is on, `observability.sh up` sets it to 1; in Helm,
   `tracing.enabled` with `tracing.sampling` (0.1 by default) and a required `tracing.endpoint`.
3. **Spans go to Tempo** over OTLP/HTTP (`CM_TRACING_ENDPOINT`, default `http://tempo:4318/v1/traces`), which keeps 3 days on a volume
   locally (256 MB, 0.25 CPU, capped). Grafana has Tempo as a data source (*Explore*, then search by service or paste a trace id).
4. **`spring.reactor.context-propagation: auto`** so the trace follows a request through the reactive chain, not just the first thread.
5. **What a span holds:** the method, status, outcome, the request path and the timings that Spring records for an HTTP request; nothing is added.
   Checked on a request with `?destination=a@example.com&code=123456`: the span's attributes were `method`, `status`, `outcome`,
   `uri` and `http.url`, and `http.url` was the path only. No body, header value or query string. **The path itself is kept**, so a path with an
   identifier in it (a consent or data principal id) is in the trace; traces are kept 3 days locally and are as sensitive as the access log.

## Not yet / limits

- **Spring Security's observations stay off** (ADR-070), so a trace shows the request and its outgoing calls, not each filter.
- **Database, Pulsar and Redis calls are not spans:** the R2DBC and Pulsar clients are not instrumented by this change, so a trace ends
  at the service boundary and its time is in the service's own span.
- **The link from a log line to its trace is not verified.** Spring Boot's default log pattern has a slot for the trace id, which fills
  while a traced request is being handled, but no log line written during one was looked at here; Grafana's Tempo data source is
  not configured to jump to Loki.
- **No sampling policy beyond a fixed share;** keeping every slow or failed trace needs a collector in front (tail sampling).
- **Checked live** on the gateway and the notification service only (images rebuilt, Tempo started, sampling 1): both exported spans; a
  request carrying a `traceparent` joined that trace with the sent span as its parent. A request through the gateway to a service
  was not followed, because the gateway refused it (no API key) before routing, so **the gateway's outgoing propagation is not
  checked**; the other seven services share the same configuration and were compiled, not started.
- **In a cluster** the endpoint is the platform's collector or Tempo; the chart only passes the setting.
