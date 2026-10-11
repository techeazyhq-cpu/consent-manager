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
| [006](adr-006-consent-token-and-pep-pdp.md) | Signed consent tokens, enforced at the gateway with OPA | Accepted, token handling superseded by 013, token lifetime by 027 (proposed) |
| [007](adr-007-reactive-stack-and-schema-per-service.md) | Reactive services on R2DBC, one PostgreSQL schema per service | Accepted |
| [008](adr-008-identity-keycloak-and-passwordless-sign-in.md) | Keycloak for identity, passwordless sign-in by one-time code | Accepted |
| [009](adr-009-independent-micro-frontends.md) | Three independent micro-frontends, no shared shell | Accepted |
| [010](adr-010-notification-delivery-through-notification-service.md) | Deliver notifications through notification-service | Accepted |
| [011](adr-011-container-images-from-built-jars.md) | Service images wrap the built JAR, and run unprivileged | Accepted |
| [012](adr-012-quality-gates.md) | Quality gates on every pull request, as notification-service has them | Accepted |
| [013](adr-013-verified-consent-tokens.md) | Consent tokens are verified, and bound to their artefact and fiduciary | Accepted, offline verification superseded by 027 (proposed) |
| [014](adr-014-tamper-evident-audit-ledger.md) | A tamper-evident audit ledger, hash-chained like a blockchain | Accepted |
| [015](adr-015-ledger-checkpoints-anchored-by-a-timestamp-authority.md) | Ledger checkpoints, anchored by an external RFC 3161 timestamp authority | Accepted |
| [016](adr-016-circuit-breakers-on-every-synchronous-call.md) | A circuit breaker on every synchronous call to another service | Accepted |
| [017](adr-017-platform-staff-and-operator-role.md) | Platform staff in their own service, with an operator role | Accepted |
| [018](adr-018-regional-cells-for-data-residency.md) | Regional cells for data residency | Proposed |
| [019](adr-019-identity-vault-and-crypto-shredding.md) | An Identity Vault holds personal data; erasure by crypto-shredding | Proposed |
| [020](adr-020-regime-profiles-as-versioned-data.md) | Regime profiles as versioned, reviewed data | Proposed |
| [021](adr-021-principal-requests-own-data-and-relay.md) | Principal requests: rights over the platform's own data, and relay to fiduciaries | Proposed |
| [022](adr-022-children-guardians-and-representatives.md) | Children, guardians and other representatives | Proposed |
| [023](adr-023-pure-consent-manager-boundary-and-neutrality.md) | A pure consent manager: its boundary, neutrality and First Schedule duties | Proposed |
| [024](adr-024-hipaa-authorisations-and-fhir-consent.md) | HIPAA authorisations in the consent model; FHIR Consent at the boundary | Proposed |
| [025](adr-025-iso-27560-consent-records-and-receipts.md) | Consent records and receipts to ISO/IEC TS 27560, in DPV | Proposed |
| [026](adr-026-consent-validity-requested-maximum-principal-adjustable.md) | Consent validity: requested and maximum per purpose, adjustable by the principal | Proposed |
| [027](adr-027-short-lived-tokens-status-list-and-verification-availability.md) | Short-lived consent tokens, a revocation status list, and verification availability | Proposed |
| [028](adr-028-accessible-consent-without-deceptive-design.md) | An accessible consent experience, without deceptive design, with recorded evidence | Proposed |
| [029](adr-029-fiduciary-assurance-and-consent-termination.md) | Fiduciaries are verified at onboarding; termination by a fiduciary or an authority | Proposed |
| [030](adr-030-contracts-interoperability-and-portability.md) | Contract-first interfaces, interoperability, and moving to another consent manager | Proposed |
| [031](adr-031-purpose-versions-re-consent-and-sharing-consent.md) | Purpose definitions are versioned; a material change needs re-consent; sharing consent binds both parties | Proposed |

## Writing one

Copy the shape of an existing record: a title that states the decision, **Status** and **Date**, then **Context**
(the forces, without the answer), **Decision** (numbered, specific enough to check the code against) and
**Consequences** (including the costs and any known gap). Use the next free number and add it to the table. To change
a decision, write a new record that supersedes the old one, and mark the old one's status, rather than rewriting
history.
