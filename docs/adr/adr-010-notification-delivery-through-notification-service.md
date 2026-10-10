# ADR-010: Deliver notifications through notification-service

- **Status:** Accepted
- **Date:** 2026-10-10 (#149, #150)

## Context

Principals must hear about new consent requests and receive sign-in codes by email, SMS, WhatsApp or app push.
Real delivery needs provider integrations, retries, failover, rate limits, OTP priority and delivery records with
erasure, which is a product of its own. TechEazyHQ already has one:
[notification-service](https://github.com/techeazyhq-cpu/notification-service).

## Decision

1. **`cm-ntf-svc` keeps the channel ports** (`EmailSenderPort`, `SmsSenderPort`, `WhatsAppSenderPort`,
   `PushSenderPort`); `cm.ntf.provider` chooses the adapters: `local` (Mailpit and in-memory catchers, the default, no
   other stack needed) or `notification-service`.
2. **With `notification-service`, one adapter (`NotificationServiceClient`) calls its client API**,
   `POST /v1/notifications`, as a tenant with its own API key:
   - each message's `ref` (`cm-notify-dp-<cnsReqId>`, `cm-otp-<uuid>`, or per device for push) is both the
     `Idempotency-Key` and the `clientReference`, so a redelivered command never sends twice;
   - sign-in codes go as `category: OTP` with the same validity as the code, so they are delivered first and never
     late; everything else is `TRANSACTIONAL`;
   - 429, 5xx and timeouts are retried with back-off; any other refusal is final (`NtfDeliveryRejectedException`),
     acknowledged by the Pulsar consumer instead of redelivered forever, and a 422 for an OTP;
   - requests are signed (NS1-HMAC-SHA256) when a signing secret is configured.
3. **Web push.** A principal turns on notifications in the DP portal; the browser's Firebase Cloud Messaging token is
   stored per principal (`ntf.push_dvc`, at most 10 devices, a token belongs to whoever registered it last). A new
   consent request for a linked principal is pushed to each device with the notice title only: the push travels
   through third-party push services, so the full notice stays in the portal. notification-service delivers it
   through its FCM provider.

## Consequences

- Provider integrations, retries, failover and delivery records live in one place for every TechEazyHQ product.
- consent-manager depends on notification-service's availability for real delivery; the outbox and Pulsar
  redelivery absorb outages for notices, and the sign-in page tells the person when a code could not be sent.
- notification-service has no delivery callbacks yet: CM knows a message was accepted, not that it was read.
- Running both stacks locally needs the port overlay in `infra/notification-service/`.
