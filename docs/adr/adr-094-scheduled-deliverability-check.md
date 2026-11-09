# ADR-094: A weekly run of the deliverability check

- **Status:** Accepted
- **Date:** 2026-11-09
- **Issue:** #104
- **Completes:** the "no scheduled run" limit of [ADR-071](adr-071-email-deliverability-check.md)

## Context

The check for the sending domain's SPF, DKIM, DMARC and MTA-STS records (ADR-071) is run by hand. These records are changed by other
people (the DNS owner, the mail provider) for other reasons, and a change that breaks one is found when sign-in codes stop arriving.

## Decision

1. **A GitHub Actions workflow runs the check every Monday** and on demand (`.github/workflows/email-deliverability.yml`), with the
   same script, so what a person runs and what the schedule runs cannot differ.
2. **It does nothing until `CM_MAIL_DOMAIN` is set** as a repository variable, and says so in the run (a notice, not a failure): the
   repository does not know the operator's domain, and a template repository or a fork should not fail every week for lack of one.
   `CM_DKIM_SELECTORS` (comma-separated) is optional; without it DKIM is a warning, as by hand.
3. **A failed record fails the run** (the script's exit code 1), and GitHub tells the people who watch the repository's Actions. Warnings
   do not fail it.
4. **The variables reach the script through the environment,** not by being spliced into the shell text.

## Not yet / limits

- **The notice is GitHub's:** an email to those watching Actions, not a page. A team that wants it in a chat or on call routes the failed
  run there (GitHub's notification settings, or the metrics' Alertmanager receiver, ADR-087).
- **Bounce and complaint handling are still open** (the other half of #104): they are the notification service's, and need a provider's
  feedback channel (an SNS topic, a webhook) that depends on which provider is used.
- **It checks what DNS says,** not that the provider signs with the published key (ADR-071).
- **Not run against the operator's domain:** it was run here against `example.com` and a name that does not exist (exit 0 and 1), and
  the workflow passed actionlint. It has not yet run on GitHub's schedule.
