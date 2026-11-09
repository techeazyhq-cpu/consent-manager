# ADR-107: Alerts on a consumer that is behind or stuck

- **Status:** Accepted
- **Date:** 2026-11-09
- **Issue:** #98
- **Builds on:** [ADR-087](adr-087-datastore-metrics-and-alert-routing.md) (Pulsar's metrics were already scraped)

## Context

Every event between the services goes through Pulsar, and each consumer is a subscription. If one stops, or keeps refusing a message,
nothing is down: the broker is up, the service answers, and the only sign is a backlog that grows or never clears: the audit ledger
missing entries, a fiduciary not told, a projection stale. ADR-087 scraped the metrics and left the backlog unalerted, "because what a normal
backlog is depends on the topic".

## Decision

1. **`PulsarSubscriptionNeverCaughtUp` (page):** a subscription whose backlog has not been empty at any point in 15 minutes, for 5
   minutes. It needs no size: a consumer that is stopped, one that keeps refusing a single message (its backlog is one, forever), and
   one far behind all look the same. A healthy consumer reaches zero between bursts, however busy.
2. **`PulsarSubscriptionBacklogLarge` (ticket):** more than 1000 messages behind for 10 minutes: a consumer that is alive and not
   keeping up. **1000 is a guess,** not a measured limit (see below).
3. **Only the platform's subscriptions:** those whose names start with two underscores (compaction, system readers) are the broker's
   own and are left out.
4. **The rules are unit-tested** with `promtool test rules` (CI runs them, ADR-092): a consumer that always catches up, a stopped one,
   a poison message that keeps the backlog at one, and the broker's own subscriptions.

## Measured here

- **A baseline:** with the local stack idle, all 108 `pulsar_subscription_back_log*` series were 0: a healthy consumer sits at zero, which
  is why the first rule needs no threshold.
- **The metric moves as the rules assume:** with the audit service stopped and 40 events queued for it, its subscription's
  `pulsar_subscription_back_log_no_delayed` showed 40 within seconds, and 0 again about 40 seconds after the service was started
  (its startup included). The duplicate events were of one real event, which the audit ledger records once (it has one entry for it).
- **Not measured:** a busy day. 1000 comes from "far more than a consumer is ever behind while keeping up", not from traffic.

## Not yet / limits

- **Not fired live:** the rules need 10 to 15 minutes of a real backlog in a running Prometheus; the logic is proved by the unit
  tests and the metric by the observation above, not by an alert arriving.
- **A managed Pulsar** (StreamNative, or a cloud's service) may name the metrics differently or expose them another way; the rules
  assume the open-source broker's names.
- **A busy consumer that is genuinely always behind by a little** would page. None here is; if one is, the fix is the consumer, not the rule.
- **The size threshold is for the platform's subscriptions as a whole,** not per topic: a topic that normally runs deep would need its
  own line.
- **A message that is refused and redelivered with a delay** counts in the backlog, so a poison message is seen; one that is
  dead-lettered is not, and there is no dead-letter alert yet.
- **Alerts still have no receiver** (ADR-087): they are seen in Alertmanager and Prometheus until someone sets one.
