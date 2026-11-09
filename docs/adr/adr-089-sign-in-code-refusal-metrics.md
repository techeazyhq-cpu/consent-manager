# ADR-089: Counting the sign-in codes that are refused

- **Status:** Accepted
- **Date:** 2026-11-09
- **Issue:** #105

## Context

The notification service now refuses a request to send a sign-in code in two ways: when it would pass the send limit (ADR-080, 429
`otp-send-limited`) and when it lacks the service key (ADR-083, 401 `internal-key-invalid`). Both ADRs listed the same gap: "no
metric or alert on it". A refusal was visible only to someone reading the notification service's log, and the two mean very different
things. A rise in limit refusals is someone asking for codes in bulk, or a limit that is too tight for real traffic; a request without the
key means Keycloak's key is wrong, so people cannot sign in, or something other than Keycloak is asking.

## Decision

1. **One counter with a reason:** `cm.ntf.otp.send.refused` (`cm_ntf_otp_send_refused_total` in Prometheus), tagged `reason=limited` or
   `reason=key`, counted by a web filter that wraps the key check, so both refusals are seen. Both series exist at zero from start-up,
   so "none refused" is a number and an alert on a rise has something to rise from. Nothing else is a tag: no address, number or code.
2. **Two alerts** (`sign-in.rules.yml`): more than 20 limit refusals in an hour (a ticket: look at who is asking, or loosen the limit),
   and any request without the key in ten minutes (a page: sign-in is broken or something is probing the endpoint).
3. **A dashboard panel,** sign-in codes refused per hour by reason.
4. **Checked:** four unit tests (both counters at zero; each refusal counted under its reason; a sent code, a rejected number, other
   paths and methods not counted; nothing but the reason in the metric); live: with the local stack, two wrong keys counted as `key` 2,
   and three codes sent then two refused counted as `limited` 2, and no address or code anywhere in the exported metrics.

## Not yet / limits

- **Wrong guesses are still counted in Keycloak, in memory** (ADR-080); this covers what the notification service refuses, not what Keycloak
  refuses. Keycloak's own refusals are events (`cm_otp_rate_limited`, ADR-061), not metrics here.
- **The threshold of 20 an hour is a guess** for a platform whose traffic is not known; it is a starting point to tune from the
  counts.
- **No breakdown by destination or caller,** deliberately: either would put an address or a service in a metric.

## Options considered

- **Counting in the handler that answers 429:** misses the 401, which is refused before any handler runs.
- **A log line only:** what existed; nobody can alert on a sentence.
