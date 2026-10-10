# ADR-002: Clean architecture inside every service, enforced by a test

- **Status:** Accepted
- **Date:** 2026-08-30 (Phase 1), recorded 2026-10-10

## Context

The consent lifecycle has to stay correct while its adapters change: Pulsar may be swapped, the HTTP interface grows,
notification delivery moved from local catchers to notification-service. Layering that is only a convention erodes
the first time a handler imports a repository implementation "just this once".

## Decision

1. **Each service is five Gradle modules**, and the module graph itself encodes the dependency rule:

   | Module | Package | Contains | May depend on |
   |---|---|---|---|
   | `-dom` | `..dom` | Aggregates, value objects, domain events, specifications, ports | the shared kernels only |
   | `-app` | `..app` | Command and query handlers (use cases), the saga orchestrator | `-dom` |
   | `-infra` | `..infra` | R2DBC repositories, outbox writer and relay, Pulsar producers and consumers, HTTP clients | `-dom`, `-app` |
   | `-if-rest` | `..ifc` | WebFlux functional routes and handlers, DTOs | `-dom`, `-app` |
   | `-boot` | root | `@SpringBootApplication`, the composition root (`*BeanConfig`), configuration, Flyway migrations | all of the above |

   `ifc` rather than `if` because `if` cannot be a Java package segment.
2. **Domain and application code is framework-free**: no Spring annotations in `-dom` or `-app`; their objects are
   created in `-boot`'s bean configuration, so the use cases are plain constructors and are unit-testable without a
   container.
3. **An ArchUnit test in every service** (`LayeredArchitectureTest` from `cm-test-kit`, applied by the
   `cm.clean-arch` convention plugin) fails the build when a layer reaches outward, or when infrastructure and
   interface code depend on each other.
4. **Gradle convention plugins** in `backend/build-logic` (`cm.spring-service`, `cm.clean-arch`) keep every service's
   build identical.

## Consequences

- Swapping an adapter touches `-infra` and `-boot` only: delivering notifications through notification-service
  ([ADR-010](adr-010-notification-delivery-through-notification-service.md)) changed no use case.
- Five modules per service is more files and more wiring than a single module; the convention plugins and the
  layering test make it cheap to follow and impossible to forget.
- The layering test is part of `./gradlew build` and therefore of the required CI check
  ([ADR-012](adr-012-quality-gates.md)).
