# ADR-055: A Redis cache in front of the consent read model

- **Status:** Accepted
- **Date:** 2026-11-04
- **Relates to:** [ADR-003](adr-003-cqrs-consent-command-and-query.md) (the read model),
  [ADR-027](adr-027-short-lived-tokens-status-list-and-verification-availability.md) (live status from the write side)

## Context

Every read of one consent in `cm-cns-qry-svc`, whether a principal opening it, a fiduciary looking it up, or a
history or verification check of ownership, goes to PostgreSQL. The Phase 1 plan put a Redis cache in front of these
reads (#31), and it was never built. Since ADR-027, the PDP asks `cm-cns-cmd-svc` for live status, so the cache no
longer sits on the enforcement path. It still takes repeated single-consent reads off the database the read model
shares with every other service.

## Decision

1. **Single-consent lookups only.** `RedisCachedCnsArtRdMdlRepo` wraps the read-model repository and caches
   `findByCnsArtId` and `findByCnsReqId` under `cns:rdmdl:art:<id>` and `cns:rdmdl:req:<id>`. Lists, counts, insights
   and the principal's dashboard read PostgreSQL as before: they change with every event and are keyed by party, not
   by consent.
2. **The projector writes through it.** Every upsert, and linking a principal, evicts that consent's keys once
   PostgreSQL has the change. A miss is not cached, so a consent not yet projected is found as soon as it is.
3. **A short TTL bounds staleness** (`cm.cnsqry.cache.ttl`, 1 minute). A read that started before a change can put the
   old row back after its eviction; the TTL is how long that can last.
4. **The cache is never needed to answer.** Redis slower than `cm.cnsqry.cache.timeout` (200 ms), unreachable, or
   holding an unreadable entry, and the read goes to PostgreSQL. A failed eviction is logged, not retried: the entry
   lapses with its TTL. Redis is left out of the service's health, so a Redis outage doesn't make it unready.
5. **The status endpoint is never cached.** `GET /internal/cns-arts/{id}/status` reads PostgreSQL directly, so even a
   caller that still uses it is never told an old status.
6. **The same Redis as the gateway and billing:** database 0, and 1 for the sandbox (ADR-037). `redis: true` for
   `cm-cns-qry-svc` in the Helm chart. `cm.cnsqry.cache.enabled=false` turns it off.

## Consequences

- A principal or fiduciary can see a consent up to a minute old in the rare case of a read racing a change. Never the
  PDP, which asks the write side.
- One more dependency for `cm-cns-qry-svc`, though an optional one.
- Integration tests: `RedisCachedCnsArtRdMdlRepoIT` (read-through, eviction, misses, Redis down) and
  `CnsQryCacheWiringIT` (which consumers get the cache).
