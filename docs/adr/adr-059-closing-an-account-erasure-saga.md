# ADR-059: Closing an account: the erasure saga

- **Status:** Accepted
- **Date:** 2026-11-08
- **Implements:** [ADR-021](adr-021-principal-requests-own-data-and-relay.md) §3 (own-data erasure as an orchestrated
  saga) and §4 (legal holds keep pseudonymised records). Crypto-shredding ([ADR-019](adr-019-identity-vault-and-crypto-shredding.md))
  stays proposed.

## Context

A principal could export their records (ADR-046) and make requests (ADR-058), but couldn't leave: nothing erased what
the platform holds about them. That data is spread over five services:

- **cm-ntf-svc:** push devices.
- **cm-cns-cmd-svc:** the email or phone number a fiduciary used, in list rows and in published outbox events, and
  requests not yet linked to the principal.
- **cm-aud-svc:** event payloads, and the links that list a principal's ledger entries for them.
- **cm-dsr-svc:** the words of their requests.
- **Keycloak:** their account.

The records of consent must stay: a consent manager keeps them for at least seven years (DPDPA First Schedule B4). They
already name the principal by a pseudonymous id only.

## Decision

1. **An orchestrated saga in `cm-dsr-svc`** (`DpErasure`). The principal starts it from the DP portal, confirming by
   typing *CLOSE MY ACCOUNT*. One erasure can be under way per principal (a unique partial index). The steps run in
   this order:
   1. **NOTICE_AND_DEVICES** (cm-ntf-svc): an email says what happens and what is kept; their push devices are
      deleted. A notice that bounces doesn't stop the saga.
   2. **CONSENTS** (cm-cns-cmd-svc):
      - Requests sent to their email but never claimed are linked to them first.
      - Every consent in force is withdrawn and every waiting request declined, through the domain. Each fiduciary is
        told by its webhooks, and each change gets its record and receipt, as if the principal had done it.
      - Contacts are erased from list rows and from published outbox events (`dpRef.val` becomes `[erased]`).
      - The step answers with the principal's consent request ids.
   3. **AUDIT** (cm-aud-svc): payloads of entries about the principal or those requests are deleted, along with the
      subject links. The entries stay, so the hash chain and the checkpoints still verify: each entry commits to its
      payload's hash, not the payload. The principal and their requests are remembered (`aud.erased_subject`), so the
      events the erasure itself causes are chained without a payload or link.
   4. **REQUESTS** (here): their open requests are withdrawn (fiduciaries are told), and the words of every request
      and answer are erased. What was asked, of whom, and how it ended stays.
   5. **IDENTITY** (here, Keycloak Admin API): the account is deleted, which ends their sessions. It runs last, so
      the principal keeps the portal until everything else is done.
2. **The wire.** Commands go on `dsr/cmd-erasure-step` and answers come back on `dsr/evt-erasure-step-done`. Each
   service subscribes under its own name and takes only its own step (`ErasureSteps` in cm-evt-kit). Commands are
   sent directly, not through the outbox: one that is lost is sent again.
3. **Retries and STUCK.** Every minute, a step without an answer for two minutes is sent again. After 8 attempts the
   erasure becomes STUCK: logged as an error, audited, and listed in CM Admin. An administrator retries it once the
   cause is fixed ([runbook](../runbooks/erasure.md)). Every step is idempotent, and an answer for a step other than
   the current one is ignored.
4. **What is kept.**
   - Consent records and receipts, under the pseudonymous id (the legal hold).
   - The erasure's own row: id, timestamps, steps, and how many consents were withdrawn.
   - The principal's email, only while the saga runs, for the notice and for claiming unlinked requests. It is
     cleared when the saga ends.
   - Staff see the pseudonymous id only.
5. **The apps.** The DP portal's Account page says what happens and what is kept, takes the typed confirmation, shows
   progress, and signs the person out when the account is gone. CM Admin has "Account erasures", where staff see the
   erasures and administrators retry stuck ones.

## Not yet

- **Crypto-shredding** (ADR-019) for copies on Pulsar topics and in backups. Topic retention and backup expiry bound
  them for now.
- **A cooling-off period** before the saga starts. It starts at once; the typed confirmation and the notice are the
  safeguards.
- **The DP mobile app** screen.
- **Retention ends:** deleting the kept records after the hold (ADR-025 §3).
- **Contacts outside this platform:** notification-service's delivery logs and the fiduciaries' own copies. The notice
  tells the principal to ask the fiduciaries.

## Options considered

- **Choreography:** each service reacts to one `DpErasureRequested` event. Rejected by ADR-021: there would be no
  single view of progress, and no way to keep identity deletion last.
- **HTTP calls from the orchestrator to each service:** these would need new internal routes, which are
  unauthenticated today (V-11). Commands on Pulsar need none.
- **Deleting the consent records:** not allowed during the legal hold.

## Consequences

- A principal can close their account themselves, and fiduciaries learn of every consent withdrawn as a result.
- Five services take part. A service that is down delays the erasure but doesn't break it.
- The audit ledger shows erased payloads in its verification (`payloadsErased`).

## Compliance impact

- Erasure of the platform's own data: DPDPA s.12(3), GDPR Art. 17, and the Gulf laws' equivalents.
- DPDPA First Schedule B4: consent records kept, pseudonymised.
