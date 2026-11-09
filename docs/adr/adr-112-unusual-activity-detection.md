# ADR-112: Finding unusual activity from counts the platform already has

- **Status:** Accepted
- **Date:** 2026-11-09

## Context

The platform has limits (a send limit per address, an API-key rate limit, a per-fiduciary quota) and alerts on a limit being hit. A limit only catches
what someone thought of: a fiduciary whose credentials leaked and who suddenly creates hundreds of consent requests, thousands of withdrawals or a
wave of API keys stays inside every limit and is invisible until someone reads the ledger by hand. Machine learning would need a service to train and
run, labelled data this platform does not have, and features about people. A consent manager must not grow a second system that profiles the principals
it protects.

## Decision

1. **Plain statistics on counts.** For each fiduciary and each signal the detector counts events per hour from the audit ledger's chain columns
   (`df_id`, `evt_type`, `occurred_at`), never the payload. No personal data, no principal id, no address, no text is read or kept.
2. **Signals:** consent requests created, consents withdrawn, consents denied, API keys issued, webhooks created, and requests or grievances raised by
   principals (`AnomalyDetector.SIGNALS`).
3. **The score:** the last *complete* hour's count against the same fiduciary's own previous hours (14 days). The busiest 5% of the baseline is set
   aside (an earlier spike must not make later ones normal); the score is the distance from the baseline's mean in spreads, the spread being at least
   the square root of the mean (counting noise) and at least 1.
4. **A finding needs all of:** at least `minCount` events in the hour (default 20), a score of at least `threshold` (default 6), and at least
   `minHistoryHours` of the fiduciary's history (default 48), so a new fiduciary launching is not an alarm and a quiet signal going from 0 to 3 is not
   either.
5. **Findings are kept in `aud.anomaly`** (ids, a signal's name, an hour, numbers; unique per fiduciary, signal and hour, so a run repeated finds
   the same ones). It is a working list, not evidence: the hash-chained ledger is untouched.
6. **Alerts:** `cm.aud.anomaly.found{signal}` counts each new finding; `UnusualFiduciaryActivity` fires on a rise. The findings, with the fiduciary and
   numbers, are on CM Admin's *Unusual activity* page. `GET /api/aud/anomalies` and `POST /api/aud/anomalies/{id}/acknowledge` are for platform
   administrators only; a fiduciary never learns another was flagged. Acknowledging records who and when.
7. **Sign-in abuse** is watched by the notification service's counts, not by the ledger (sign-in happens in Keycloak and never reaches it): the
   service now counts codes it accepts (`cm.ntf.otp.send.accepted`) beside those it refuses (ADR-089), and `SignInCodeSurge` fires when the last
   hour is over six times the hourly average of the day before and over 50. A count, with no address, number or code.
8. **Off-switch and tuning:** `config.anomalyDetection.*` in Helm (`cm.aud.anomaly.*`). Two log codes and one problem type are in the error dictionary.

## Consequences

- **It tells, it does not decide.** A launch or a campaign looks the same as abuse. Nothing is blocked, suspended or throttled by a finding; a
  person looks and acknowledges.
- **Detection lags:** the last complete hour is judged, every ten minutes, so a burst is found between about 10 minutes and an hour and ten after it begins.
- **Blind spots:** a fiduciary in its first 48 hours is not judged; a slow, steady rise teaches the baseline; a signal that is already high is judged
  against that. Sign-in abuse is platform-wide, not per fiduciary or per address, by design (nothing identifying is counted).
- **Cost:** one grouped query over 14 days of the ledger every ten minutes, helped by an index on `occurred_at`.
