# ADR-003: CQRS for consent — separate command and query services

- **Status:** Accepted
- **Date:** 2026-08-30 (Phase 1), recorded 2026-10-10

## Context

Consent is written rarely and read constantly. Every time a fiduciary is about to process personal data it checks that
the consent artefact is still `ISSUED` (the PDP's live-status call), Data Principals open dashboards that aggregate
across fiduciaries, and fiduciaries list the requests they raised. The write model (`CnsReq` and `CnsArt` aggregates
with their invariants) is a poor shape for those reads, and read load must never slow down a consent decision.

## Decision

1. **Command side, `cm-cns-cmd-svc`:** owns the aggregates and every state change: create a request, approve, deny,
   revoke, link a principal, expire. Each change is one R2DBC transaction that saves the aggregate and its events to
   the outbox ([ADR-004](adr-004-transactional-outbox-and-pulsar.md)).
2. **Query side, `cm-cns-qry-svc`:** owns `cns_qry.cns_art_rd_mdl` (one row per request/artefact, shaped for the
   dashboards and lists), `cns_qry.cns_hst_rd_mdl` (every status transition, for history) and `cns_qry.prj_ckpt`. Its
   `Projector` consumes the `evt-cns-*` topics and upserts the read model; `prj_ckpt` records each processed `evtId`,
   so a redelivered event is applied once.
3. **The query side serves:** the DP dashboard, "my consents" for a principal or a fiduciary (scope always taken from
   the caller's JWT, never from a parameter), an artefact's history, and the internal live-status endpoint the PDP
   calls ([ADR-006](adr-006-consent-token-and-pep-pdp.md)).
4. **Read-your-writes in the UI is handled at the edge:** after an approve, deny or revoke, the DP portal polls the
   read model briefly until the row's status changes, instead of the command side returning read-model data.

## Consequences

- Reads scale independently and cannot block writes; the read model can be rebuilt by replaying the topics.
- The read side is eventually consistent, typically within a second locally; three write-then-read races found in
  Phase 1 were fixed by polling in the UI rather than by weakening the split.
- The PDP's live-status check reads the projection, so a revocation takes effect for enforcement once projected. The
  lag is bounded by the outbox relay interval plus projection time; the expiry sweep keeps the projection honest for
  artefacts that lapse without any user action ([ADR-005](adr-005-consent-lifecycle-saga.md)).
- Two services to deploy and monitor for one domain.
