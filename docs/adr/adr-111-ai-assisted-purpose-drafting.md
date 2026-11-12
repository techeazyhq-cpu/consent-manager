# ADR-111: Drafting a purpose with a model the operator runs

- **Status:** Accepted
- **Date:** 2026-11-09
- **Refined by:** [ADR-113](adr-113-ai-model-provider-settings-in-cm-admin.md) (the model and provider are chosen in CM Admin; vendors' APIs are supported)

## Context

Writing a purpose well (the data categories, the retention, the notice in plain words) is the slowest part of a fiduciary's first hour. A language
model can produce a first draft from a description, but a consent record is a legal statement: a model must never decide it, never see people's
data, and never send a fiduciary's words to a third party without the operator choosing that.

## Decision

1. **A suggestion only.** `POST /api/cns-cmd/prps-defs/draft-assist` returns a purpose draft and saves nothing. The person reads it, edits it and saves it
   through the ordinary draft endpoint, which applies the same rules as for any draft.
2. **A model the operator runs.** The adapter speaks the OpenAI-compatible `POST /v1/chat/completions` (Ollama, vLLM, and others), at an endpoint the operator
   sets. **Off by default** (`cm.cnscmd.ai-drafting.enabled`); when off, the status says so and the editor does not show the panel.
3. **Only the description is sent**, and first **screened for personal data** (an email address, a PAN, an Aadhaar-shaped number, a Luhn-valid card
   number, a phone number). If any is found the request is refused with 422 `ai-draft-personal-data` before the model is called, and the answer names
   the kinds found, never the text. A name inside a sentence cannot be told, so the editor says not to paste anyone's details.
4. **Prompt injection:** the description is fenced as data in the prompt, and the model's reply is never executed or trusted: it must be one JSON
   object, which is cleaned and bounded and then checked with `PrpsVerContent.check()`, the validation of a person's draft. A reply that fails is asked
   for once more, with the reason; then 502 `ai-draft-invalid`.
5. **A person confirms what a model cannot know.** Each suggestion lists what to confirm (the numbers, who receives the data, transfers, anything a
   model judged sensitive) and warnings. The editor blocks Save until each confirm item is ticked.
6. **Limits:** a per-fiduciary hourly rate (429), a limit on concurrent model calls per pod (503), a time limit on the model, input length bounds. The
   gateway has its own route and time limit (130 s) so a slow model neither opens the circuit for the rest of cns-cmd nor is cut at 10 s.
7. **Audit** (`purpose.ai-draft`, `purpose.ai-draft-refused`): who, which model, how many characters, the outcome. **Never the text.**
8. **Six problem types** are in the error dictionary; none of the model's failures is `retryable` except 429 and 503.

## Consequences

- A small local model is slow on CPU (about 70 s for `llama3.2:3b` on a laptop) and its drafts need real checking; the confirm list and the review
  warnings are the guard, not a promise of quality.
- A name in free text can reach the model; it stays on the operator's own server unless the operator points the endpoint elsewhere.
- Model quality, language coverage and cost are the operator's to choose; nothing here depends on a vendor.
