# ADR-005: The consent lifecycle as an orchestrated saga

- **Status:** Accepted; saga state persisted and stale legs re-driven since 2026-11-03
- **Date:** 2026-08-30 (Phase 1), recorded 2026-10-10

## Context

A consent request crosses several contexts: the fiduciary's subscription must allow it (billing), the principal must
be told (notification), the decision must be told back to the fiduciary, usage must be metered, and an artefact that
lapses must expire on its own. These steps cannot share one database transaction, and a notification outage must not
stop a principal from deciding.

## Decision

1. **Local transactions first.** Each state change of the consent lifecycle is a local transaction in
   `cm-cns-cmd-svc`:

   ```text
   CnsReq:  PENDING ──approve──▶ GRANTED      (issues CnsArt: ISSUED ──revoke──▶ REVOKED
                    ──deny─────▶ DENIED                         ──expire──▶ EXPIRED)
                    ──expire───▶ EXPIRED
   ```

   Creating a request first checks, synchronously and in that order, that the fiduciary is active (`cm-df-reg-svc`)
   and that its subscription is eligible (`DfSubEligiblePolicy`, `cm-bil-svc`'s status and monthly usage), each
   behind a circuit breaker in its `-infra` adapter, so an ineligible request is refused before anything is written.
2. **An orchestrator drives the cross-service legs.** `CnsLifecycleSagaOrchestrator` (a process manager) subscribes
   to the service's own `evt-cns-*` events and issues the next step as a command: `cmd-notify-dp` after a request is
   created, `cmd-notify-df` after a decision or revocation. Driving the legs from events, not from the write path,
   means a notification failure never fails or delays the consent decision.
3. **No compensation is needed for the legs that exist.** Every leg after the decision is a notification or a
   projection; none can invalidate the decision, so failure is handled by retry (Pulsar redelivery) rather than by
   undoing a granted consent. `CnsReqSts.NOTIFICATION_FAILED` exists for a request whose principal could not be
   reached.
4. **Deferred principal linking.** A fiduciary may ask for consent by email or phone before the person has a CM
   account (`DpRef.UnlnkDpRef`). When the person signs in, `LinkDpCmdHandler` links every pending request for that
   address to their identity (`CnsDpLinkedEvt`); repeating it is a no-op.
5. **Expiry is a scheduled step.** `CnsExpirySweepJob` moves `ISSUED` artefacts past their validity period to
   `EXPIRED` and raises `CnsExpiredEvt`, so the read model and the PDP never treat a lapsed consent as live.

## Consequences

- The decision is never held hostage by a downstream outage; the platform converges through redelivery.
- **Saga state (closed gap, 2026-11-03):** each leg records itself in `cns_cmd.sga_state`: STARTED, then DONE, or
  FAILED with the error; a leg's id is derived from the leg and its subject, so a redelivered event updates the same
  row and counts an attempt. A failed leg still fails its message, so Pulsar redelivers it as before. `SgaRedriveJob`
  runs again any leg left STARTED or FAILED for `cm.cnscmd.saga-redrive.stale-after` (5 minutes), and after
  `max-attempts` (10) marks it GAVE_UP, where it stays for an operator to query. Legs are at-least-once: a notification
  may be sent twice.
- A future leg that *can* invalidate a decision (for example a fiduciary refusing an artefact) needs a compensating
  command, designed with that leg.
