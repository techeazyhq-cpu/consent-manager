# ADR-113: The drafting model is chosen in CM Admin, from Ollama, OpenAI, Anthropic, Gemini, DeepSeek or any compatible server

- **Status:** Accepted
- **Date:** 2026-11-12
- **Refines:** [ADR-111](adr-111-ai-assisted-purpose-drafting.md) §2 (the model was set by properties and an OpenAI-compatible endpoint only)

## Context

ADR-111 let an operator point drafting at one OpenAI-compatible server through `cm.cnscmd.ai-drafting.*`. Changing the model, or the provider, meant editing
configuration and restarting. The operator wants to choose it where they run the platform (CM Admin), and to be able to use a vendor's API (OpenAI,
Anthropic, Google Gemini, DeepSeek) as well as a model of their own.

A vendor's API changes one thing ADR-111 relied on: the description a fiduciary typed leaves the platform's infrastructure. ADR-111 §2 says that must never
happen unless the operator chooses it, so choosing a vendor has to be a recorded, explicit act.

## Decision

1. **One platform setting, kept in cns-cmd** (`cns_cmd.ai_drafting_config`, a single row), edited in CM Admin on the page *AI drafting model*.
   `GET /api/cns-cmd/admin/ai-drafting` is for platform staff; `PUT` and `POST …/test` are for administrators only (`CM_ADMIN`), checked at the gateway
   and again in the service. Until a setting is first saved, the `cm.cnscmd.ai-drafting.*` properties apply as before, so nothing existing breaks.
2. **Six providers, three wire protocols.** Ollama, OpenAI, DeepSeek and *any OpenAI-compatible server* (vLLM, llama.cpp, LiteLLM…) share the chat-completions
   adapter, each with its own path (DeepSeek has no `/v1`). Anthropic (Messages API) and Gemini (`generateContent`) have adapters of their own. A new
   OpenAI-shaped provider is a line in `AiProvider`; a new protocol is an adapter and a case in `DraftingModels`.
3. **A vendor's address is fixed.** Only a server the operator runs (Ollama, OpenAI-compatible) has an address they type. A vendor's endpoint is the
   vendor's own and is ignored if submitted, so a slip or a hostile edit cannot send descriptions anywhere else. An address is http(s) with a host and
   nothing else (no user, query or fragment), never a link-local or cloud metadata address, and plain `http` only for the platform's own network
   (loopback, private ranges, single-label and `.svc`/`.internal`/`.local` names).
4. **Sending text outside the platform is acknowledged.** When the setting would send descriptions to a vendor, or to a server whose address is not the
   platform's own, it cannot be switched on without `thirdPartyAck`, and the page says plainly what goes (a description, screened first as in ADR-111 §3;
   never a consent, a principal or a record). The acknowledgement and who gave it are in the audit trail.
5. **The key is write-only.** It is sealed with the service's own key (the same sealer webhooks use) before it is stored; it is never returned (the setting
   says only `apiKeySet`), never logged, never in the audit trail, and sent to a provider in a header (`Authorization`, `x-api-key`, `x-goog-api-key`), never
   in a URL. **A stored key is carried over only while the provider and address are unchanged**: changing provider drops it, so one vendor's key can
   never be sent to another. A key can be removed.
6. **Test before saving.** `POST …/test` calls the model with a one-line ping using the setting as it stands in the form (and the stored key when it still
   applies). It sends no fiduciary text and answers only whether it worked and how long it took, never what the provider said, which could echo a key. The
   gateway gives it the same 130 s route as drafting so a slow local model is not cut at 10 s.
7. **A change takes effect at once and reaches other instances within 30 s.** The saving instance swaps its client immediately; every instance re-reads the
   setting on a timer and swaps when it differs (`cm.cnscmd.ai-drafting.reload-ms`). A failed read keeps the client in use.
8. **Audit:** `platform.ai-drafting-config` (who, provider, model, enabled, whether the key changed, whether it leaves the platform) and
   `platform.ai-drafting-test` (who, provider, model, ok or failed). Never a key.
9. **Everything else in ADR-111 stands**, whichever provider answers: the reply is one JSON object, cleaned, bounded and checked with
   `PrpsVerContent.check()`, a person confirms what a model cannot know, and the limits (per-fiduciary rate, concurrency, input length) apply.

## Consequences

- A vendor's model is usually faster and better than a 3B model on a laptop, but descriptions leave the platform under the vendor's terms; the
  acknowledgement makes that the administrator's recorded choice, not a default. A privacy notice for the platform should name the vendor chosen.
- JSON output is requested with each provider's own switch where there is one (OpenAI-shaped `response_format`, Gemini `responseMimeType`); Anthropic has
  none, so it relies on the prompt, and the tolerant parse and the one retry with the reason in ADR-111 §4 are what make that safe.
- The setting is one row, not one per fiduciary: a fiduciary cannot bring its own model or key. That would make every fiduciary's key a secret this
  service keeps, and is left for a decision of its own.
- Instances can disagree for up to 30 s after a change. Acceptable for a setting changed rarely; drafting is a suggestion and saves nothing.
