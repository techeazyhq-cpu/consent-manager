# ADR-063: A fiduciary's acceptance of the consent manager's terms and data processing agreement

- **Status:** Accepted
- **Date:** 2026-11-07
- **Issue:** #111

## Context

The consent manager processes personal data for each fiduciary, so a fiduciary needs a written agreement with it: the
terms of service, and a data processing agreement (DPA). Nothing recorded that a fiduciary had agreed to either, or which
version, or who agreed for the organisation. If a dispute or a regulator's question came, there was no evidence.

## Decision

1. **Two documents, each with a version in force**: `terms` and `dpa`. The current version of each is configuration
   (`cm.dfreg.terms.terms-version`, `cm.dfreg.terms.dpa-version`, default `2026-11`), changed when the words change.
   This service holds versions, not the text: the text is published where fiduciaries read it (the DF Console).
2. **Only an Owner accepts.** It binds the organisation, so the same role that can grant ownership accepts. Administrators,
   consent managers, developers, auditors and platform staff cannot (`not-permitted`, 403).
3. **The version accepted must be the one in force** (`terms-version-outdated`, 409, naming the current one), so nobody
   accepts words they were not shown. An unknown document is `terms-document-unknown` (400).
4. **Acceptances are evidence and are only ever added.** Each row holds the fiduciary, document, version, the person's
   id and the time. `(fiduciary, document, version)` is unique, so accepting again returns the first acceptance and
   changes nothing (and is not audited twice). A new version is a new row; the old one stays as what was agreed then.
5. **Every acceptance is in the audit ledger** (`terms.accepted`, with document and version), like the other changes to
   a fiduciary (ADR-040).
6. **API.**
   - `GET /api/df-reg/me/terms`: each document's current version and whether the signed-in member's fiduciary has accepted
     it. Any member at the fiduciary.
   - `POST /api/df-reg/me/terms` with `{"document":"terms","version":"2026-11"}`: the Owner accepts. 201.
   - `GET /api/df-reg/dfs/{id}/terms`: platform staff read a fiduciary's acceptances, newest first.

## Not yet

- **Not enforced.** Activation does not wait for acceptance, and an old acceptance does not stop a fiduciary working
  after a new version is published. Enforcing it would stop every existing fiduciary at once; it needs a notice period
  and a decision on what "not accepted" blocks (activation only, or API use too). The status is there for the DF
  Console to ask, and for staff to see before they activate.
- **The DF Console does not show it yet**; this is the service and its API.
- **The text of each version** is not stored here, so the record says which version, not the words of it.
- **Where the Owner was** (address, device) is not recorded; the audit ledger holds the actor.

## Options considered

- **Block activation until accepted:** the strongest, but registration is done by staff before the Owner has signed in
  for the first time, so staff cannot be the ones to accept, and the flow would need reordering. Recording first lets
  that be decided separately.
- **A flag on the fiduciary:** one boolean cannot say which version, who, or when, and loses the history.
- **Storing the document text with each acceptance:** useful evidence, but it needs a document store and an authoring
  flow, which is a bigger decision than recording the acceptance.
