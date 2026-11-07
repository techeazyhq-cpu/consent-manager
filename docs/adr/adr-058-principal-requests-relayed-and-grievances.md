# ADR-058: Principal requests relayed to fiduciaries, and grievances about the platform

- **Status:** Accepted
- **Date:** 2026-11-07
- **Implements:** [ADR-021](adr-021-principal-requests-own-data-and-relay.md) §1 (relayed requests and grievances), §2
  (deadlines fixed at receipt, refusals cite a legal ground). Own-data rights and the erasure saga (§1, §3, §4) stay
  proposed.

## Context

A principal had nowhere in the platform to ask an organisation about the data it holds under their consent, or to
complain about the platform itself. ADR-021 proposed a Principal Requests context covering three kinds of request; the
erasure of the platform's own data is the largest of them, and needs the identity vault of ADR-019 to be complete.
Relayed requests and grievances need neither, and give principals a single place to exercise their rights now.

The platform must stay a pure consent manager (ADR-023): it passes requests on and keeps the record, but never fulfils
a request about a fiduciary's data, and never sends a fiduciary more personal data than the consent already ties to the
principal.

## Decision

1. **A new service, `cm-dsr-svc`** (port 8088, schema `dsr`, Pulsar namespace `dsr`), with one aggregate, `DpReq`, of
   two kinds:
   - **RELAY**: about one of the principal's own consents, for its fiduciary. The right is one of ACCESS, CORRECTION,
     ERASURE, GRIEVANCE (a complaint to the fiduciary) or NOMINATION (DPDPA ss.11-14). Correction, complaint and
     nomination say what they are about; access and erasure need only the consent.
   - **GRIEVANCE**: a complaint about the platform, answered by platform staff.
2. **The consent says who is asking.** A relayed request names a consent request id. `cm-dsr-svc` asks
   `cm-cns-qry-svc` for it with the principal's own token (`GET /api/cns-qry/cns-arts/{id}`), so the query service
   decides whose consent it is, and someone else's is "not found". The fiduciary identifies the principal from the
   consent in its own records; no contact detail is stored in `dsr` or sent to the fiduciary. No `/internal` route was
   added for this (V-11).
3. **Lifecycle.** OPEN → ACKNOWLEDGED → RESOLVED or REFUSED; the principal may WITHDRAW while it is open. The answer is
   text for the principal to read. A refusal must name its legal ground (a database check enforces it too). Closed is
   final.
4. **Deadlines are fixed at receipt** (`due_by`) and never move: 90 days for both kinds, from configuration
   (`cm.dsr.deadline-days`), until regime profiles carry them (ADR-020). DPDPA Rules give a grievance at most 90 days.
   A request still open past its deadline is shown as overdue to everyone, and the principal is told where to go next:
   the fiduciary's grievance officer, then the Data Protection Board (s.13(3)).
5. **Relay by webhook, at least once.** Creating or withdrawing a relayed request writes a message to `dsr`'s outbox
   in the same transaction. The relay publishes it to `evt-df-message`; `cm-cns-cmd-svc` consumes it and queues it for
   the fiduciary's webhook endpoints with the deliveries, signatures and retries of ADR-047. Two new types,
   `principal_request.received` and `principal_request.withdrawn`, carry ids, the right, the purpose code and the
   deadline: never the principal's words. Only these types are accepted from that topic. The message id is chosen by
   `cm-dsr-svc`, so a redelivered event is no second delivery.
6. **Who sees and answers.**
   - The principal: `/api/dsr/me/requests`, their own only.
   - The fiduciary: `/api/dsr/df/requests`. Every member and API client reads; Owners, Administrators, Consent
     managers and API clients answer, as they raise requests (ADR-033). Another fiduciary's request is 404.
   - Platform staff: `/api/dsr/grievances`, administrators and operators; they never see relayed requests.
7. **Limits.** One open request per consent and right (a unique partial index), and at most 20 open requests per
   principal, so the platform can't be used to flood a fiduciary.
8. **Audit.** Every change is recorded with who made it (ADR-040) on `dsr`'s `evt-platform-action` topic, against the
   fiduciary for a relayed request so it shows in the fiduciary's own audit. Details hold the right, consent, status and
   refusal ground, never the principal's words.
9. **The apps.** The DP portal's "Your rights" page makes requests and complaints and shows each answer; a consent's
   history links to it. The DF Console has "Principal requests"; CM Admin has "Grievances". Both use one queue
   component from ui-kit.

## Not yet

- **Own-data rights and erasure** (ADR-021 §1, §3, §4): access, correction and the erasure saga, with legal holds.
- **Notifying the principal** by email or push when an answer arrives. They see it in the portal.
- **The DP mobile app** screen.
- **Identity or representative verification** before fulfilment (ADR-021 §2, ADR-022). The platform fulfils nothing
  itself yet; signing in with a one-time code is the principal's verification for raising a request.
- **The sandbox.** It has no `cm-dsr-svc`; sandbox principals are synthetic.
- **Deadlines per regime** (ADR-020), and reminders to the answerer before the deadline.

## Options considered

- **Requests inside `cm-cns-cmd-svc`:** it already sends webhooks, but requests have their own lifecycle, answerers and
  retention, and ADR-021 gives them their own context.
- **Calling `cm-cns-cmd-svc` to send the webhook synchronously:** a failure would lose the message or the request; the
  outbox keeps them in one transaction.
- **An internal route on `cm-cns-qry-svc` to look up a consent by id:** another unauthenticated internal surface (V-11);
  the principal's own token answers the same question with the ownership check already there.

## Consequences

- Principals can exercise their rights against a fiduciary, and complain about the platform, in one place with a
  deadline and a record.
- Fiduciaries must answer in the DF Console or through the API, and may subscribe their webhooks to the new types.
- One more service per cell, small (3 database connections, 384 MiB requested).

## Compliance impact

- DPDPA ss.11-14 and Rules (grievance redressal within 90 days), relayed where Rule 14 permits **[confirm]**.
- DPDPA First Schedule: a consent manager's records of what it relayed and when.
- GDPR Arts. 12, 15-17 timelines are shorter (one month): a regime profile must set them before an EU cell runs this.
