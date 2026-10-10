# ADR-004: Transactional outbox to Apache Pulsar, at-least-once with idempotent consumers

- **Status:** Accepted
- **Date:** 2026-08-30 (Phase 1), recorded 2026-10-10

## Context

When a consent is granted, the artefact must be saved *and* the rest of the platform must hear about it: the read
model, the notifications, and (with the audit ledger) the permanent record. Saving to PostgreSQL and publishing to a broker in two steps loses
the event if the process dies in between, or publishes an event for a change that rolled back. A distributed
transaction across PostgreSQL and the broker is not available with R2DBC and Pulsar, and would couple a consent
decision to broker availability.

## Decision

1. **Outbox.** Each write-side service owns an `outbox_evt` table (`cns_cmd.outbox_evt`, `df_reg.outbox_evt`). The
   aggregate collects its `DomainEvt`s (`Agg.pullEvts()`), and the infrastructure writes them as `ObxRow`s in the same
   R2DBC transaction as the aggregate itself.
2. **Relay.** `ObxRelay` polls rows with `published_at IS NULL` on a fixed delay, publishes each to its topic and marks
   it published. A broker outage delays events; it never blocks or fails a consent decision.
3. **Apache Pulsar** carries events and commands. Topic names follow
   `persistent://cm/<context>/<evt|cmd>-<name>-v1` (`PulsarTopics`): tenant `cm`, one namespace per bounded context,
   and a version suffix so a breaking contract change gets a new topic. Events (`evt-cns-granted`, `evt-df-activated`,
   ...) state facts; commands (`cmd-notify-dp`, `cmd-notify-df`) ask a context to do something.
4. **At-least-once, idempotent consumers.** Every envelope (`EvtEnvelope`) carries an `evtId`; consumers deduplicate
   on it (the projector's `prj_ckpt`, idempotency keys towards notification-service). A handler failure negatively
   acknowledges the message so Pulsar redelivers it; a permanent rejection is acknowledged with a warning so it does
   not loop.
5. **Consumers subscribe from the earliest position**, so a message published before a consumer's first subscription
   is not missed.

## Consequences

- No lost or phantom events, without distributed transactions.
- Every consumer must be idempotent; new consumers dedupe on `evtId` or use it as an idempotency key downstream.
- Ordering is per topic only; consumers must not assume cross-topic order.
- The tenant and namespaces must exist before services start (`infra/pulsar/init-tenant-namespaces.sh`).
- The relay is a poller: events appear after up to one polling interval. Change data capture can replace it if that
  becomes too slow.
- The tamper-evident audit ledger consumes these same topics, so it records exactly what the platform acted on.
