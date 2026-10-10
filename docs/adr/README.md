# Architecture decision records

Each significant decision is recorded here: its context, what was decided, and what follows from it, the good and the
bad. ADR-001 to ADR-011 record Phase 1's architecture as the code implements it; the plan they came from was not kept
in the repository, so where the code and the plan disagreed, the record follows the code and names the gap.

| ADR | Decision | Status |
|---|---|---|
| [001](adr-001-bounded-contexts-and-services.md) | One service per bounded context, in the language of the DPDPA (DDD) | Accepted |
| [002](adr-002-clean-architecture-modules.md) | Clean architecture inside every service, enforced by a test | Accepted |
| [003](adr-003-cqrs-consent-command-and-query.md) | CQRS for consent: separate command and query services | Accepted |
| [004](adr-004-transactional-outbox-and-pulsar.md) | Transactional outbox to Apache Pulsar, at-least-once with idempotent consumers | Accepted |
| [005](adr-005-consent-lifecycle-saga.md) | The consent lifecycle as an orchestrated saga | Accepted, saga state not persisted yet |
| [006](adr-006-consent-token-and-pep-pdp.md) | Signed consent tokens, enforced at the gateway with OPA | Accepted, token handling superseded by 013 |
| [007](adr-007-reactive-stack-and-schema-per-service.md) | Reactive services on R2DBC, one PostgreSQL schema per service | Accepted |
| [008](adr-008-identity-keycloak-and-passwordless-sign-in.md) | Keycloak for identity, passwordless sign-in by one-time code | Accepted |
| [009](adr-009-independent-micro-frontends.md) | Three independent micro-frontends, no shared shell | Accepted |
| [010](adr-010-notification-delivery-through-notification-service.md) | Deliver notifications through notification-service | Accepted |
| [011](adr-011-container-images-from-built-jars.md) | Service images wrap the built JAR, and run unprivileged | Accepted |
| [012](adr-012-quality-gates.md) | Quality gates on every pull request, as notification-service has them | Accepted |
| [013](adr-013-verified-consent-tokens.md) | Consent tokens are verified, and bound to their artefact and fiduciary | Accepted |
| [014](adr-014-tamper-evident-audit-ledger.md) | A tamper-evident audit ledger, hash-chained like a blockchain | Accepted |

## Writing one

Copy the shape of an existing record: a title that states the decision, **Status** and **Date**, then **Context**
(the forces, without the answer), **Decision** (numbered, specific enough to check the code against) and
**Consequences** (including the costs and any known gap). Use the next free number and add it to the table. To change
a decision, write a new record that supersedes the old one, and mark the old one's status, rather than rewriting
history.
