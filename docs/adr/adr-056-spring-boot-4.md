# ADR-056: Spring Boot 4, with the services' JSON kept on Jackson 2

- **Status:** Accepted
- **Date:** 2026-11-04
- **Relates to:** [ADR-007](adr-007-reactive-stack-and-schema-per-service.md) (the reactive stack),
  [docs/security/vapt-and-performance.md](../security/vapt-and-performance.md) (V-04, V-13)

## Context

The VAPT image scan found two CRITICAL vulnerabilities in Spring WebFlux 6.2.19 (CVE-2026-47890, CVE-2026-47892),
fixed only in Spring Framework 7.0.9. Framework 7 comes with Spring Boot 4. Both were accepted in `.trivyignore` until
2027-01-31 (#216).

Spring Boot 4 is more than a version bump:
- Auto-configuration is split into modules: Flyway, WebClient, the security test support and WebTestClient each have
  their own.
- Jackson 3 (`tools.jackson`) is the default. 37 of the services' main classes use Jackson 2's `ObjectMapper`, for
  events, outbox payloads, receipts and records.
- Spring Cloud Gateway 5 renames its starter and moves its settings under `spring.cloud.gateway.server.webflux`.

## Decision

1. **Spring Boot 4.0.9** (Framework 7.0.10, Security 7.0.8, Reactor 3.8.8, Netty 4.2.19) with **Spring Cloud
   2025.1.4** (Gateway 5.0.4, CircuitBreaker 5.0.3), on **Gradle 8.14.3**, the oldest Gradle Boot 4's plugin accepts.
2. **The services stay on Jackson 2 for now.** The `cm.spring-service` convention adds `spring-boot-jackson2`, which
   auto-configures the Jackson 2 `ObjectMapper` the code injects, and each service sets
   `spring.http.codecs.preferred-json-mapper: jackson2`, so request and response bodies are read and written exactly as
   before. Moving to Jackson 3 changes defaults that reach the wire (property order, nulls for primitives) and touches
   every module, so it is a change of its own, not part of a security fix. Boot 4 deprecates Jackson 2 support, so it
   has to happen before Boot removes it.
3. **The modules Boot 4 split out are declared:** `spring-boot-starter-flyway` wherever Flyway is,
   `spring-boot-starter-webclient` in the convention (5 services inject `WebClient.Builder`),
   `spring-boot-starter-webflux-test` and `spring-boot-starter-security-test` for the gateway's tests.
4. **Pulsar stays pinned at 4.0.14.** Boot 4.0.9 manages Pulsar 4.1.3, whose `bouncy-castle-bc` jar nests BouncyCastle
   1.78.1, older than before; 4.0.14 nests 1.86 (#217).
5. **The overrides Boot 3.5 needed are gone:** Netty 4.1 and Jackson's BOM. Boot 4 manages patched versions of both,
   and the old overrides would have pulled them back.

## Consequences

- The WebFlux CVEs are fixed and their `.trivyignore` entries removed, so CI's image scan checks them again.
- Verified beyond the tests: all eight services start in the local stack, Flyway migrates, and
  `scripts/verify-phase1.sh` passes all 15 steps, from registration to withdrawal and the email in Mailpit.
- Each image carries both Jackson 2 and Jackson 3, about 3 MB more, until the move to Jackson 3.
- The services still declare `pulsar-client` 3.3.9 alongside the 4.0 client API jars, as before; aligning them is part
  of the same follow-up.
