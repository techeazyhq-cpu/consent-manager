# ADR-043: Purpose versions in the Consent context; requests name a published version

- **Status:** Accepted
- **Date:** 2026-10-19
- **Implements:** [ADR-031](adr-031-purpose-versions-re-consent-and-sharing-consent.md) §1 and §2 (sharing consent,
  §3 and §4, comes later)

## Context

A consent request carried a free-text purpose code, its own list of data categories, and its own notice. Nothing tied
a consent to a definition of the purpose, so a fiduciary could widen what it asked for, or change the notice, with no
trace and no re-consent. ADR-031 decided that purposes are versioned. ADR-026, partial consent and 27560 receipts
(ADR-025) all build on that.

The platform hasn't launched, so no fiduciary depends on the free-text request.

## Decision

1. **A purpose is an aggregate of `cm-cns-cmd-svc`** (`PrpsDef`), with its versions (`PrpsVer`), as ADR-031 puts it in
   the Consent context. A version holds:
   - a name and description
   - data categories, each with its DPV type, sensitivity, `necessary` flag and consequence text (ADR-026 §5)
   - recipients and transfers
   - retention, requested validity and maximum validity, in days
   - special-processing flags (ADR-022)
   - one notice per language, each identified by the SHA-256 of its title, a blank line and its text
2. **At most one draft; published versions never change.** A draft can be edited or discarded. Publishing makes it the
   current version. The table refuses any update or delete of a published row (a trigger), so a bug can't rewrite
   what people agreed to.
3. **The platform classifies each new version** against the current one, field by field (`PrpsChange`):
   - **Material:** more data, a raised sensitivity, new recipients or transfers, longer retention, a longer maximum
     validity, or a new flag.
   - **Non-material:** everything else.
   - **Refused:** a draft identical to the current version.
   The console shows the classification and its reasons before publishing. `PrpsVerPublishedEvt` carries them for the
   notifications and webhooks that follow.
4. **A request names `prpsCode` and `prpsVer`.** Optionally it also names `ntcLang` (default: English, else the first
   written) and `dCatCodes` (default: all the version's).
   - **Only the current version can be asked for.** An older version is refused with 409
     `purpose-version-superseded`, so a stale integration fails loudly instead of asking for a superseded scope.
   - **A category the version doesn't cover is refused (400).** This is purpose limitation.
   - The request keeps the version's id and number, the notice language, and the notice's hash. Its title and text
     are copied from the version, so the principal's portal and the read model are unchanged.
   - Batches (ADR-036) name a version the same way.
5. **Who:** purposes are read by all of a fiduciary's people, and changed by those who raise requests (Owner,
   Administrator, Consent manager) and by its API clients (ADR-033). Every change goes to the audit ledger (ADR-040).
6. **Migration (`V3__purpose_versions`):** each purpose code already used by a request or batch becomes a purpose.
   - **Version 1 is published from what those requests asked:** the union of their data categories, and the latest
     notice as the English notice.
   - It keeps the 365 days every consent was given for.
   - Old requests point to that version 1 but keep the hash of the notice they actually carried, which may be an
     earlier wording. The record stays truthful.
7. **Interim limits until regime profiles (ADR-020):**
   - validity is a number of days, at most 3,650; event-based validity comes with ADR-020
   - retention is at most 36,500 days

## Options considered

- **A separate purposes service:** another deployable and another network hop on every request, for an aggregate the
  Consent context owns and checks in the same transaction.
- **Allow requests for any published version:** simpler for integrations, but a fiduciary could keep asking for a
  scope it has since replaced. The current version is what the fiduciary says it does now.
- **Keep the free-text request for a deprecation period:** two request shapes and weaker purpose limitation, with no
  one yet depending on the old one.

## Consequences

- Every request, and later every receipt, can be traced to the exact data, recipients, validity and notice the
  principal was shown.
- Fiduciaries must define and publish a purpose before asking for consent. The console, the sandbox playground, the
  Postman collection and the OpenAPI contract start there.
- **This is a breaking change** to `POST /api/cns-cmd/cns-reqs` and `/cns-req-batches`: `ntcTitle`, `ntcText` and a
  free choice of categories are gone.
- The validity figures are stored, but consents still last 365 days. Using the principal's chosen validity is the
  next step (ADR-026), as are partial consent at grant and telling principals of non-material changes.
