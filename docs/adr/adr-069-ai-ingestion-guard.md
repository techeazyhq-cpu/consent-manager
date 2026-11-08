# ADR-069: A consent guard for AI ingestion pipelines

- **Status:** Accepted
- **Date:** 2026-11-08
- **Issue:** #96

## Context

Fiduciaries are building retrieval and assistant features that turn customers' text into embeddings in a vector database. That is
processing of personal data for a purpose, and it is the hardest kind to undo: a vector does not say whose data it came from, and a
fine-tuned model cannot forget. The Consent Manager already answers the questions a pipeline needs (is this consent in force for
this purpose, right now: ADR-027) and already tells the fiduciary when a consent changes (webhooks: ADR-047). What was missing is
the pattern that puts those two in front of and behind an ingestion pipeline.

## Decision

1. **A reference library, `sdk/ai-ingestion-guard`** (zero dependencies, one file), and a guide, rather than a service. The
   Consent Manager stays a consent manager: it verifies consent and tells fiduciaries about changes (ADR-023); what a fiduciary does
   with its pipeline is its own, and the library shows the pattern against the public API alone.
2. **Check before embedding.** `IngestionGuard.ingest` verifies each item's consent for the pipeline's purpose and calls the
   embedding model only for what is allowed. The model never sees text that consent does not allow. If embedding is slow, the
   consent is checked again before the vector is stored.
3. **Three outcomes, never two.** `ALLOW`, `DENY` (no consent, withdrawn, another purpose: skip, do not retry) and
   **`UNAVAILABLE`** (the check could not be made: defer and retry). A 503 or a network failure is never treated as a refusal or as an
   approval, which is what the API's `consent-check-unavailable` is for.
4. **Vectors carry their consent** (`cnsArtId`, purpose, categories, time), the only way a vector can be found again when its
   consent ends.
5. **Act on change two ways.** A verified webhook (`consent.withdrawn`, `consent.lapsed`, and `consent.modified` with a withdrawn
   category) removes the vectors at once; a scheduled `reconcile` re-verifies every consent the store holds and removes those no
   longer allowed, which catches a webhook that never arrived. During `reconcile`, a consent that cannot be checked keeps its vectors.
6. **Webhooks are verified as the platform documents** (Standard Webhooks: HMAC-SHA256, constant-time, 5-minute window, rotation) and
   de-duplicated on `webhook-id`; a forged or unsigned delivery deletes nothing.
7. **Tested** by 19 unit tests and an end-to-end script that runs a throwaway principal's three consents through the real stack:
   ingest, a withdrawal delivered by a verified webhook, forged deliveries refused, a lost webhook caught by `reconcile`, and data
   offered after withdrawal never reaching the embedding model.

## Not yet / limits

- **Models already trained.** Removing vectors does not remove what a model learned. The guide says plainly not to fine-tune on data
  whose consent can be withdrawn; the Consent Manager cannot enforce that.
- **Derived copies** (summaries, caches, logs, chunk stores outside the vector index) are the fiduciary's to track under the same
  `cnsArtId`; the library only manages the store it is given.
- **Other languages.** Only Node is provided. The pattern is four API calls and one signature check; a Python port is the obvious
  next one.
- **Consent changes between ingestion and use at query time** are not checked here: that is the verification call the fiduciary
  already makes before using data (ADR-027). A retrieval layer that checks the vectors' `cnsArtId` before showing results would
  close the gap between a withdrawal and the next `reconcile`; it is a few lines and is described in the README, not built.
- **Real vector stores.** Only the in-memory reference store exists; adapters for pgvector, Qdrant or Pinecone are small but untested here.

## Options considered

- **A Consent-Manager-hosted ingestion gateway** that every pipeline calls: it would put the platform in the data path of
  fiduciaries' AI workloads and make it a processor of the data it is meant only to guard. The verification API is the right line.
- **Checking at query time only:** the data has already been processed and embedded by then, and removing it is the hard part.
- **Deleting on `UNAVAILABLE`** (fail closed on the stored data): an outage of the platform would erase a fiduciary's index. The data
  is already stored; waiting until the check can be made is the safe side.
