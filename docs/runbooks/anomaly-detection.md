# Unusual activity

Decision: [ADR-112](../adr/adr-112-unusual-activity-detection.md). The audit service compares each fiduciary's activity in the last complete hour with its
own last 14 days. It reads counts only, never a payload, and it tells; it does not block anything.

## When `UnusualFiduciaryActivity` fires

1. Open **CM Admin → Unusual activity** (administrators). Each finding names the fiduciary, what was unusual (consent requests, withdrawals, denials,
   API keys, webhooks, principal requests), the hour, and the count against the usual.
2. Open the fiduciary (its id links to its page) and ask whether there is an ordinary reason: a launch, a campaign, a migration, a batch job.
3. If there is none, think of credentials: for *API keys issued* or *webhooks created*, check the key list and webhooks of the fiduciary and revoke
   what it did not make; for a surge of requests, look at the API key making them in the audit tab.
4. Suspend the fiduciary only through the ordinary offboarding steps ([offboarding](offboarding.md)); the detector never does.
5. **Acknowledge** the finding once understood. It records who and when and leaves the open list.

## When `SignInCodeSurge` fires

Codes sent in the last hour are more than six times the day's hourly average and over 50. It is platform-wide: the numbers carry no address.
Compare with `SignInCodesRefusedByTheLimit` (the limit working). A bot is stopped at the network edge or by lowering `cm.ntf.otp-limits.*`; a
real event (a launch, a mailing) needs nothing.

## Tuning

Helm `config.anomalyDetection`: `minCount` (default 20) and `threshold` (default 6, in spreads) set how unusual an hour must be; `minHistoryHours`
(48) how long a new fiduciary is left to settle; `baselineDays` (14) what is usual; `enabled: false` turns the look off. Too many findings: raise
`minCount` first. Too few: lower `threshold`, not below about 4.

## Local

`CM_ANOMALY_INTERVAL_MS=60000 CM_ANOMALY_MIN_HISTORY_HOURS=1 docker compose up -d cm-aud-svc` looks every minute with a short history;
`node tests/security/e2e-anomaly.mjs` bursts API keys and waits for the hour to end, so it takes up to an hour.

## What it does not catch

A fiduciary in its first 48 hours; a slow rise over days; abuse that stays within its usual volume; sign-in abuse of one address or one person
(that is the send limits' job, [ADR-061](../adr/adr-061-sign-in-code-abuse-limits.md)).
