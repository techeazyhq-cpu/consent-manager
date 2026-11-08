# ADR-071: Checking the sending domain's email authentication

- **Status:** Accepted
- **Date:** 2026-11-08
- **Issue:** #104

## Context

Sign-in codes, consent requests and notices are email, and a person who never receives one cannot give, see or withdraw a consent.
In production the mail is sent by TechEazyHQ's notification service (cm-ntf-svc calls it; Mailpit is for development only), so the
SMTP connection, the provider account and the signing keys belong to that service. What the Consent Manager's operators control is
the sending domain's DNS: whether SPF, DKIM and DMARC are published and set so that receivers trust the mail, and spoofed mail
claiming to be from the domain (a convincing fake consent request) is refused.

## Decision

1. **A checker, not a mailer.** `scripts/email-deliverability.mjs <domain> [--dkim-selector s1]` reads the domain's TXT records and
   reports pass, warn or fail for SPF (one record, ending `-all` or `~all`, at most ten lookups, never `+all`), DKIM (a published
   key of at least 2048 bits, not revoked, not in test mode), DMARC (a policy of quarantine or reject, with an `rua` report address
   and at `pct=100`) and MTA-STS (advisory). It exits non-zero only on a failure, so warnings (`p=none`, while a domain is being
   introduced) do not block.
2. **Pure rules, tested without a network.** Each check is a function over TXT strings with seven unit tests, run in CI; only the
   DNS lookups need a network.
3. **A runbook** says what to publish and in what order (monitor with `p=none`, read the reports, then `quarantine`, then `reject`),
   and what to do when a customer says the mail never came.

## Not yet / limits

- **It does not send mail or read bounces.** Delivery, bounces and complaints are the notification service's; the Consent Manager
  records only that it asked for a message to be sent.
- **It cannot see the provider's signing.** A published DKIM key says nothing about whether the provider signs with it: send a test
  message to a mailbox that shows the headers (`dkim=pass`, `spf=pass`, `dmarc=pass`) after any change.
- **SPF `redirect=` and nested includes are counted, not followed.** A domain that redirects is reported for a human to check.
- **No scheduled run.** It is run by hand or from a pipeline; wiring it to an alert is open.
- **Reverse DNS and sending IP reputation** are the provider's.

## Options considered

- **Sending a test email and checking headers automatically** needs a mailbox the platform controls and a provider account in CI.
- **Sending mail from cm-ntf-svc over SMTP with its own signing.** That duplicates what the notification service owns.
