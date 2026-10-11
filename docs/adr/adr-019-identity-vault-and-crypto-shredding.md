# ADR-019: An Identity Vault holds personal data; erasure by crypto-shredding

- **Status:** Proposed
- **Date:** 2026-10-11
- **Design:** [compliance architecture §6.2](../compliance/compliance-architecture.md)

## Context

A principal's email or phone travels in `CnsReqCreatedEvt`, `CnsDeniedEvt` and `CnsNotificationFailedEvt` (`dpRef`),
sits in `cns_cmd` outbox rows and request tables, and is retained by Pulsar. The audit ledger keeps payloads apart so
they can be erased (ADR-014), but nothing can erase the copies on topics, in outbox history or in backups. Every
regime in scope grants erasure, with legal holds that require keeping some records, pseudonymised.

## Decision

1. **A new service, `cm-vlt-svc` (Identity Vault), is the only holder** of subjects' contact points, names, dates of
   birth and representatives.

   Every other context, event and command carries a pseudonymous `dpId`. A fiduciary raising a request by email gets
   the `dpId` from the vault first (created if new, so the answer never reveals whether a person exists).
2. **Each subject has a subject key** (DEK), wrapped by the cell's KMS key.

   - Personal fields are encrypted with it in the vault, and wherever another store must keep a personal value.
   - The value then travels and rests as ciphertext plus key id, never in clear.
3. **Erasure deletes the live rows, then destroys the subject key.**

   Every remaining ciphertext copy becomes unreadable: outbox history, topic retention, audit payloads, backups.
   Backups also expire by the strictest erasure period, and restores re-apply a list of shredded subjects.
4. **Notifications resolve contact points from the vault at send time.** Commands carry `dpId` only.
5. **Keycloak keeps the login identity** needed to sign in. Erasure deletes the user (ADR-021).
6. **Fitness functions:**
   - No event schema may contain contact fields.
   - No class outside `cm-vlt-*` may hold contact-data types (ArchUnit).
   - Topics and stores are scanned for PII patterns in staging.

## Options considered

- **Tombstone events and rewrite topics:** can't reach backups, and is fragile on log compaction.
- **Keep PII in each service and delete per service:** erasure becomes a cross-cutting search, and copies on the event
  bus remain.
- **Event sourcing with per-subject encryption only:** the consent context doesn't need full event sourcing (ADR-003).

## Consequences

- G1 is closed, and erasure becomes one decisive act per subject.
- The vault is on the request path for raising requests and sending notifications. It must be HA, sits behind a
  circuit breaker (ADR-016) and fails closed for writes.
- Key management is now critical. A lost KMS key loses a cell's personal data, so key backups and rotation are run
  per cell.
- Existing rows are migrated: contact values are moved into the vault, and `dp_ref_val` is replaced with `dpId`.

## Compliance impact

Covers erasure rights under all regimes (DPDPA s.12, GDPR Art. 17, Gulf laws), data minimisation and security of
processing. HIPAA's minimum-necessary standard is supported by keeping PHI-adjacent contact data in one place.
