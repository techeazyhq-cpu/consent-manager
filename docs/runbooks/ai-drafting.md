# Drafting purposes with a model you run

Decision: [ADR-111](../adr/adr-111-ai-assisted-purpose-drafting.md). Off by default. A fiduciary describes a purpose in words and gets a draft to read, edit and
save; nothing is saved or published for them.

## Switch it on

1. Run a model with an OpenAI-compatible endpoint, for example Ollama (`ollama pull llama3.1:8b`) or vLLM.
2. **Helm:** set `config.aiDrafting.enabled: true`, `endpoint` and `model`; `apiKeySecretKey` if the endpoint wants a bearer key (a key in the release's secret).
   Let `cm-cns-cmd-svc` reach it: add the model's namespace to `networkPolicy.egress.namespaces` (or its address to `networkPolicy.egress.external`).
3. **Local stack:** `CM_AI_DRAFTING_ENABLED=true` (Ollama on the host is reached as `host.docker.internal:11434`; `CM_AI_DRAFTING_ENDPOINT` and `CM_AI_DRAFTING_MODEL` change it).
4. Check: `GET /api/cns-cmd/prps-defs/draft-assist/status` says `enabled: true` with the model; `node tests/security/e2e-ai-drafting.mjs` runs the whole path.

## What is sent, and what is kept

The fiduciary's description, after it is screened for an email address, PAN, Aadhaar-shaped number, card number and phone number, and nothing else. The audit
trail records who drafted, with which model, how many characters and the outcome, never the text.

## When it goes wrong

| What they see | Meaning | What to do |
|---|---|---|
| Panel missing | drafting is off or the status call failed | check `enabled`, the endpoint, the status call |
| 422 `ai-draft-personal-data` | the description holds personal data | remove it; the answer names the kinds |
| 429 `ai-drafting-rate-limited` | over `perHour` | raise `config.aiDrafting.perHour` or wait |
| 503 `ai-drafting-busy` / `-unavailable` | too many calls at once, or the model is down or timed out | raise `maxConcurrent` or the model's capacity; check the endpoint |
| 502 `ai-draft-invalid` | the model's reply broke the purpose rules twice | try a clearer description or a larger model |
