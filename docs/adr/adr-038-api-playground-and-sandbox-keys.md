# ADR-038: The API playground in the DF Console, and sandbox keys taken with one click

- **Status:** Accepted
- **Date:** 2026-10-19
- **Implements:** [ADR-034](adr-034-sandbox-and-api-playground.md) §2 (the playground), in part §4; builds on
  [ADR-037](adr-037-running-the-sandbox.md)

## Context

Fiduciaries asked to try the consent request API (single, batch and file upload, ADR-036) before integrating.
ADR-034 puts a playground in a developer portal, calling only the sandbox. There is no developer portal yet. The DF
Console is where a fiduciary's people already sign in, with roles, including **Developer** (ADR-033: "API clients and
webhooks in the sandbox").

To call the sandbox, the playground needs sandbox API keys for the person's fiduciary. Sandbox realms are separate
from production's (ADR-037), so a production DF Console sign-in can't get a sandbox token directly. Something has to
connect the two.

## Decision

1. **The playground is a page in the DF Console** ("API playground", under Developers). It can call the sandbox's
   gateway and nothing else:
   - a catalogue of the fiduciary's calls (raise a request, the read model, batches by JSON or CSV upload, a batch's
     progress and rows, consent token keys), with test-contact samples
   - path parameters filled from earlier answers (the batch just created, the latest request)
   - the full response with status and timing
   - "Copy as curl", with the token and secret left as shell variables, never pasted in
   - a link to the sandbox's inbox, where every message the sandbox "sends" lands
2. **Sandbox keys with one click.** `POST /api/df-reg/sandbox-keys` exists only in the sandbox
   (`cm.sandbox.enabled`).
   - It is the one route where the sandbox believes a production token. Its own security chain, in the sandbox's
     gateway and in its cm-df-reg-svc, trusts only production's fiduciary realm (`cm-df`). Every other sandbox route
     still trusts only the `sbx-` realms.
   - The token says which fiduciary and which role. Owners, Administrators and Developers may take keys; Consent
     managers and Auditors may not (403).
   - The first time, the sandbox makes a **twin** of the fiduciary and activates it, which creates its `df-api-<id>`
     client in `sbx-cm-df`. The twin has the same id (so asking again finds it), the name the console sends, a test
     contact address (`sandbox+…@sandbox.example`) and the STANDARD plan, so 10,000-row batches fit. Nothing else of
     production's crosses over.
   - Every later call **replaces** the secret, and the old one stops working.
   - The secret is in that response only (`Cache-Control: no-store`). The console holds it in the page's memory, never
     in browser storage, so a reload forgets it and the person takes or enters keys again.
3. **People without a key role** can enter keys an Owner, Administrator or Developer shared with them.

## Options considered

- **Keys issued by platform staff, pasted in.** No trust between the environments at all, but slow and manual: a
  ticket for every developer and every rotation. Rejected for onboarding friction. Pasting is still possible (3).
- **A separate sign-up in the sandbox** (ADR-034 §4 as written): developers register in `sbx-cm-df` and are attached to
  their fiduciary once verified. It is the long-term shape for a developer portal outside the console, but it needs the
  verification step and another sign-in. The one-click route serves the console's people now, and that sign-up can
  arrive later beside it.

## Consequences

- A developer goes from signing in to their first sandbox call in two clicks, with no ticket.
- **Production to sandbox trust is one way and narrow.** The sandbox believes production's fiduciary realm on one
  route, for who is asking, and learns only the fiduciary's id and display name. Production never believes a sandbox
  token, and the sandbox never calls production.
- **Anyone with the key role can rotate the secret,** which breaks integrations using the old one. The console says so
  next to the button. Separate secrets per person or system come with API client management (ADR-033's follow-up).
- **A sandbox reset (ADR-037) removes twins and their clients.** The next "Get sandbox keys" makes them again, and the
  playground tells a person with stale keys to take new ones.
- **Not yet:** the principal simulator (giving and withdrawing consent as a test person), so verify-before-use and
  token refresh can't be tried end to end in the playground; the webhook inspector; conformance checks.

## Compliance impact

- No personal data reaches the sandbox: the twin carries an id and a name, and requests take test contacts only.
- Secrets aren't logged, written to browser storage, or put in copied commands.
