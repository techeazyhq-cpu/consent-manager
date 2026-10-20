# ADR-047: Fiduciary webhooks, signed and delivered at least once from the outbox

- **Status:** Accepted
- **Date:** 2026-10-20
- **Implements:** [ADR-027](adr-027-short-lived-tokens-status-list-and-verification-availability.md) §6.3 webhooks (the
  token lifetime and status list stay proposed); DPDPA s.6(6) and Rule 3 (a fiduciary acts on a withdrawal promptly)

## Context

A fiduciary learns that consent was given, narrowed or withdrawn only by polling the request or verifying a token.
Withdrawal must stop processing promptly, and a fiduciary with many consents can't poll each one. ADR-027 proposed
webhooks to tell it sooner. Every change of a consent is already a domain event written to the outbox in the
change's own transaction (ADR-046), so there is one place to start a delivery that can't be lost.

## Decision

1. **Messages, one per change.** `WhkMessage` maps each domain event to a type:
   - `consent.requested`, `consent.notification_failed`, `consent.given`, `consent.refused`
   - `consent.modified`, `consent.renewed`, `consent.withdrawn`, `consent.lapsed`
   - `receipt.issued`

   The body is `{id, type, occurredAt, data}`. `data` has ids, the W3C DPV status, the purpose code and what is in
   force: categories, validity, and for a change what changed. It never has personal data: no contact and no
   principal id. The fiduciary reads more, such as the 27560 record, through the API. The message id is the domain
   event's id, the same on every endpoint, attempt and replay, so a receiver de-duplicates on it.
   `webhook.test` is sent on request only.
2. **At least once, from the outbox transaction.** `ObxWriter` writes a `whk_delivery` row for each active endpoint
   that wants the type, in the same transaction as the change, its outbox row and its record event. A change is never
   committed without its deliveries, and a delivery never exists for a change that rolled back.
3. **Delivery.** `WhkDeliveryWorker` polls every second. It claims up to 50 due deliveries with
   `FOR UPDATE SKIP LOCKED`, under a 60-second lease, so replicas share the work and a crash only delays a delivery.
   It posts up to 8 at once, with a 10-second timeout, and never follows redirects. A 2xx delivers. Anything else, or
   no answer, is retried after 1 and 5 minutes, then 30 minutes, 2, 6, 12 and 24 hours: 8 attempts over about 45
   hours. Then the delivery is **FAILED** (dead-lettered) and kept. Every attempt is recorded with its status or
   error and duration.
4. **Signatures: Standard Webhooks.** Each endpoint has a secret `whsec_` + base64 of 24 random bytes. Each request
   carries:
   - `webhook-id`: the message id
   - `webhook-timestamp`: Unix seconds
   - `webhook-signature`: `v1,` + base64 HMAC-SHA256 of `id.timestamp.body`

   The Standard Webhooks libraries verify these in any language. Receivers should refuse timestamps more than 5
   minutes off.
5. **Rotation without a gap.** Rotating makes a new secret and keeps the old one for 24 hours. Meanwhile both
   signatures are sent, space-separated, so the fiduciary switches its verifier at its own pace. A secret is shown
   once, when made or rotated, and kept sealed with AES-256-GCM under `cm.cnscmd.secrets-key`. That sealer moved to
   cm-sec-kit, shared with df-reg's API client secrets.
6. **No SSRF.** An endpoint must be `https`, with no credentials or fragment in its URL. Its host must resolve only to
   public addresses. Loopback, private, link-local (cloud metadata), carrier-grade NAT, benchmark, multicast,
   unique-local and IPv4-mapped forms of these are refused, at registration and again before every send, since DNS
   can change. Only the local stack allows private addresses and `http` (`cm.webhooks.allow-private`); the Helm chart
   sets it false.
7. **The API and the console.** `/api/cns-cmd/webhooks/...` covers:
   - endpoints: list, add, read, remove, pause and resume, rotate the secret, send a test
   - deliveries: list by endpoint and status, read with every attempt, replay a delivered or failed one

   A replay goes out now, with the same id. Every fiduciary member reads; Owners, Administrators, Developers and API
   clients change (ADR-033). Another fiduciary's endpoints and deliveries are 404. At most 10 endpoints each. Each
   change is audited with who made it (ADR-040). The DF Console's Developers section has a Webhooks page.
8. **The contract** is [df-webhooks.asyncapi.yaml](../api/df-webhooks.asyncapi.yaml) (AsyncAPI 3).

## Not yet

- **Ordering.** Messages for one consent can arrive out of order after a retry. Receivers order by `occurredAt`, or
  read the consent's current state.
- **Mutual TLS or IP allow-lists** for receivers that want more than the signature.
- **Retention of deliveries.** They are kept with their endpoint; a purge of old delivered rows comes with the
  retention work of ADR-025 §3.

## Consequences

- A withdrawal reaches the fiduciary within seconds, not at its next poll, and is never silently lost.
- Each consent change adds one delivery row per subscribed endpoint, in the same transaction.
- An endpoint that is down for two days needs a replay of what failed; the console lists failures to replay.
