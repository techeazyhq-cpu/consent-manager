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
| [009](adr-009-independent-micro-frontends.md) | Three independent micro-frontends, no shared shell | Accepted; channels extended by 032, DF sign-in superseded by 033 (proposed) |
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
| [025](adr-025-iso-27560-consent-records-and-receipts.md) | Consent records and receipts to ISO/IEC TS 27560, in DPV | Accepted (046); inclusion proofs, PDF/UA and archive proposed |
| [026](adr-026-consent-validity-requested-maximum-principal-adjustable.md) | Consent validity: requested and maximum per purpose, adjustable by the principal | Accepted (044, 045); renewal reminders proposed |
| [027](adr-027-short-lived-tokens-status-list-and-verification-availability.md) | Short-lived consent tokens, a revocation status list, and verification availability | Proposed; webhooks (§6.3) accepted in 047 |
| [028](adr-028-accessible-consent-without-deceptive-design.md) | An accessible consent experience, without deceptive design, with recorded evidence | Proposed |
| [029](adr-029-fiduciary-assurance-and-consent-termination.md) | Fiduciaries are verified at onboarding; termination by a fiduciary or an authority | Proposed |
| [030](adr-030-contracts-interoperability-and-portability.md) | Contract-first interfaces, interoperability, and moving to another consent manager | Proposed |
| [031](adr-031-purpose-versions-re-consent-and-sharing-consent.md) | Purpose definitions are versioned; a material change needs re-consent; sharing consent binds both parties | Accepted for versions and re-consent (043); sharing proposed |
| [032](adr-032-channels-per-audience-web-mobile-console-developer-portal.md) | Channels per audience: DP web and mobile, DF Console, developer portal | Proposed; DP mobile app accepted in 050 |
| [033](adr-033-fiduciary-staff-accounts-separate-from-api-clients.md) | Fiduciaries' people have their own accounts; API clients are for systems only | Proposed |
| [034](adr-034-sandbox-and-api-playground.md) | A sandbox with synthetic principals, and an API playground in the developer portal | Proposed |
| [035](adr-035-a-keycloak-realm-per-audience.md) | A Keycloak realm per audience: data principals, fiduciaries, platform staff | Accepted |
| [036](adr-036-batches-of-consent-requests.md) | Batches of consent requests: one template, many people, from JSON, CSV or Excel | Accepted |
| [037](adr-037-running-the-sandbox.md) | Running the sandbox: the same images, its own stores, realms and keys, shared servers | Accepted |
| [038](adr-038-api-playground-and-sandbox-keys.md) | The API playground in the DF Console, and sandbox keys taken with one click | Accepted |
| [039](adr-039-billing-visible-to-fiduciaries-plan-change-requests.md) | Billing fiduciaries can see, plan changes they ask for, and a platform billing overview | Accepted |
| [040](adr-040-audit-every-change-with-who.md) | Audit every change on the platform, with who made it | Accepted |
| [041](adr-041-api-keys-and-signed-requests.md) | API keys and HMAC-signed requests against replay, for fiduciaries' systems | Accepted |
| [042](adr-042-deploying-to-aws-azure-and-gcp.md) | Deploying to AWS, Azure and GCP with Terraform and one Helm chart | Accepted |
| [043](adr-043-purpose-versions-in-the-consent-context.md) | Purpose versions in the Consent context; requests name a published version | Accepted |
| [044](adr-044-partial-consent-and-validity-at-grant-by-the-principal-only.md) | Partial consent and the validity chosen at grant, by the principal only | Accepted |
| [045](adr-045-shorten-withdraw-a-category-renew-and-lapse.md) | Shortening, withdrawing a category, renewing, and lapse | Accepted |
| [046](adr-046-consent-records-receipts-and-export.md) | Consent records written with each change; receipts signed with their own key; export | Accepted |
| [047](adr-047-fiduciary-webhooks.md) | Fiduciary webhooks, signed and delivered at least once from the outbox | Accepted |
| [048](adr-048-console-members-have-their-own-burst-limit.md) | People in the DF Console have a per-second limit of their own; the plan's is for API clients | Accepted |
| [049](adr-049-dashboard-v1-and-developer-docs.md) | Dashboard v1 (outcomes and time to decide) and developer docs in the DF Console | Accepted |
| [050](adr-050-dp-mobile-app.md) | The DP mobile app: Expo in the frontend workspace, shared rules, PKCE sign-in, secure storage | Accepted |
| [051](adr-051-dp-mobile-push-notifications.md) | Push notifications in the DP mobile app: FCM tokens on both platforms, through notification-service | Accepted |
| [052](adr-052-resource-limits-for-the-local-stack.md) | CPU and memory limits for the local stack | Accepted |
| [054](adr-054-common-namespace-on-kubernetes.md) | The common namespace on Kubernetes: one chart for the shared servers, the apps in their own namespaces | Accepted |

## Writing one

Copy the shape of an existing record: a title that states the decision, **Status** and **Date**, then **Context**
(the forces, without the answer), **Decision** (numbered, specific enough to check the code against) and
**Consequences** (including the costs and any known gap). Use the next free number and add it to the table. To change
a decision, write a new record that supersedes the old one, and mark the old one's status, rather than rewriting
history.
