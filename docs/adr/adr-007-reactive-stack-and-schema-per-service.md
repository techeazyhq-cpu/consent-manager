# ADR-007: Reactive services on R2DBC, one PostgreSQL schema per service

- **Status:** Accepted
- **Date:** 2026-08-30 (Phase 1), recorded 2026-10-10

## Context

The services spend most of their time waiting on I/O: PostgreSQL, Pulsar, Keycloak, OPA and each other. The gateway
and the verification path in particular must handle many concurrent calls cheaply. Each bounded context must own its
data ([ADR-001](adr-001-bounded-contexts-and-services.md)), but a separate database server per service is more than the
platform needs to run today.

## Decision

1. **Spring Boot WebFlux everywhere**, functional routes (`RouterFunction`) rather than annotated controllers, and
   **R2DBC** (`DatabaseClient`) for PostgreSQL. Event-loop threads are never blocked; the few blocking calls (SMTP in
   the local mail adapter) run on `Schedulers.boundedElastic()` with the reason stated.
2. **Flyway owns each schema**, run at service start over JDBC: the one deliberate blocking step, isolated to startup.
   Migrations live in the owning service's `-boot` module (`db/migration/V<n>__*.sql`) and are never edited once merged.
3. **One PostgreSQL database (`consent_manager`), one schema per service**: `df_reg`, `cns_cmd`, `cns_qry`, `bil`,
   `ntf`. A service connects with its schema as the default and never queries another's.
4. **HTTP/2 (h2c)** is enabled on every service; clients that cannot negotiate it (Java's `HttpClient` from Keycloak)
   pin HTTP/1.1.

## Consequences

- High concurrency on few threads; back-pressure end to end.
- Reactive code is harder to read and debug than blocking code, and every library on the path must be non-blocking.
- Schema-per-service keeps ownership clear and makes a later physical split mechanical (epic #101): each service
  already behaves as if it had its own database. Until then the services share one server's capacity and failure.
- Schema ownership is a rule enforced by review and by each service's credentials in production, not by PostgreSQL in
  the local stack, where all services share one user.
