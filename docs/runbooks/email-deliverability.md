# Runbook: email deliverability

[ADR-071](../adr/adr-071-email-deliverability-check.md). Check a sending domain:

```bash
node scripts/email-deliverability.mjs consent.example.com --dkim-selector s1
```

Fix every `FAIL`; read every `WARN`.

A workflow runs it every Monday ([ADR-094](../adr/adr-094-scheduled-deliverability-check.md)). Turn it on by setting the repository variables
`CM_MAIL_DOMAIN` (and optionally `CM_DKIM_SELECTORS`, comma-separated) under Settings, Secrets and variables, Actions, Variables; until then
it only says it has nothing to check. A red run means a record failed: run the command above and read the `FAIL` lines.

## What to publish

| Record | Where | Value |
|---|---|---|
| SPF | TXT at the domain | `v=spf1 include:<your mail provider's include> -all`, one record only, at most ten lookups |
| DKIM | TXT at `<selector>._domainkey.<domain>` | the provider's 2048-bit public key; the provider tells you the selector |
| DMARC | TXT at `_dmarc.<domain>` | `v=DMARC1; p=quarantine; rua=mailto:dmarc@<domain>; pct=100` |
| MTA-STS | TXT at `_mta-sts.<domain>` and a policy file | optional; protects mail *to* the domain |

## Introducing DMARC

Start with `p=none` and an `rua` address. Read the reports for a few weeks: every legitimate sender (the notification service,
the support desk, the billing system) must pass SPF or DKIM aligned with the domain. Then `p=quarantine`, and when nothing
legitimate is quarantined, `p=reject`.

## "The code never arrived"

1. Is the address right, and in the principal's spam folder? The sign-in flow does not reveal whether an address exists (ADR-065).
2. `docker logs infra-cm-ntf-svc-1` (or the pod's log) for the send; the notification service's own log for the delivery outcome.
3. Send a test message from the same path to a mailbox that shows headers. `dkim=fail` or `spf=fail` means a record above is wrong,
   or the provider is not signing with the published key.
4. Run the check again after any DNS change; records can take the TTL to propagate.
