# Drafting purposes with a model you run

Decision: [ADR-111](../adr/adr-111-ai-assisted-purpose-drafting.md). Off by default. A fiduciary describes a purpose in words and gets a draft to read, edit and
save; nothing is saved or published for them.

## Choose the model (CM Admin)

Decision: [ADR-113](../adr/adr-113-ai-model-provider-settings-in-cm-admin.md). Sign in to CM Admin as a platform administrator and open **AI drafting model**
(operators can look, only administrators change it).

1. **Provider:** Ollama or any OpenAI-compatible server you run (vLLM, llama.cpp, LiteLLM), or a vendor's API: OpenAI, Anthropic, Google Gemini, DeepSeek.
2. **Model:** the vendor's model name (`gpt-4o-mini`, `claude-haiku-5-5`, `gemini-2.0-flash`, `deepseek-chat`) or what your server serves (`llama3.2:3b`).
3. **Server address:** only for a server you run. A vendor's address is fixed. Plain `http` is for your own network only; anything else must be `https`.
4. **API key:** a vendor needs one; a server of yours may. It is write-only: it is sealed when saved and never shown again. Leave the field blank to keep the stored
   key, or tick *Remove the stored key*. **Changing provider drops the stored key**: enter the new vendor's own.
5. **A vendor, or a server outside your network, sends fiduciaries' descriptions outside the platform.** You must tick the acknowledgement before you can switch it on;
   who did, and when, is in the audit trail. Say so in your privacy notice.
6. **Test the connection** before saving: it sends one line (no description) with the form as it stands, and says whether it worked and how long it took.
7. **Save.** It is used at once on that instance and within 30 s on the others (`cm.cnscmd.ai-drafting.reload-ms`).

Check: `GET /api/cns-cmd/prps-defs/draft-assist/status` says `enabled: true` with the model; `node tests/security/e2e-ai-drafting.mjs` runs the whole path.

### Before anyone has saved a setting

The service's own configuration applies, as in ADR-111, and CM Admin says so. Saving the page replaces it; the properties are then ignored.

1. **Helm:** `config.aiDrafting.enabled: true`, `endpoint` and `model`; `apiKeySecretKey` if the endpoint wants a bearer key. Let `cm-cns-cmd-svc` reach it: add the model's
   namespace to `networkPolicy.egress.namespaces` (or its address to `networkPolicy.egress.external`). **A vendor's API needs that egress too**: add its address to
   `networkPolicy.egress.external`, or the test and every draft will fail as "could not be reached".
2. **Local stack:** `CM_AI_DRAFTING_ENABLED=true` (Ollama on the host is reached as `host.docker.internal:11434`; `CM_AI_DRAFTING_ENDPOINT` and `CM_AI_DRAFTING_MODEL` change it).

## Turn it on for a tenant

Decision: [ADR-114](../adr/adr-114-ai-drafting-per-tenant-at-onboarding-or-on-request.md). Choosing a model does not give any tenant drafting: **each tenant is
off until staff turn it on.** A tenant without it is refused with 403 `ai-drafting-not-enabled` before anything is sent to the model.

- **At onboarding:** tick *Draft purposes with the AI model* on *Onboard tenant*. It is off by default.
- **Already onboarded:** CM Admin → *AI drafting model* → *Organisations that can draft* → *Turn on for an organisation*.
- **When a tenant asks:** its purpose editor shows *Draft with AI* with *Ask to turn it on* (when the platform has a model). The request appears under
  *Requests waiting* with its note. An **administrator** approves it, or declines it with a reason the tenant sees. Operators can see requests and turn drafting
  on, but not decide a request or turn it off.
- **Turning off:** an administrator, from the *Turned on* list. It applies to the tenant's next draft.

Every change is in the audit trail (`ai-drafting.access-*`) with who and which tenant, never the note or the reason.

## What is sent, and what is kept

The fiduciary's description, after it is screened for an email address, PAN, Aadhaar-shaped number, card number and phone number, and nothing else. The audit
trail records who drafted, with which model, how many characters and the outcome, never the text.

## When it goes wrong

| What they see | Meaning | What to do |
|---|---|---|
| Panel missing | the platform has no model, or the status call failed | check the model under *AI drafting model*, and the status call |
| *Ask to turn it on* shown | the tenant has not been given drafting | turn it on for the tenant, or approve its request |
| 403 `ai-drafting-not-enabled` | the tenant has no access | as above |
| 422 `ai-draft-personal-data` | the description holds personal data | remove it; the answer names the kinds |
| 429 `ai-drafting-rate-limited` | over `perHour` | raise `config.aiDrafting.perHour` or wait |
| 503 `ai-drafting-busy` / `-unavailable` | too many calls at once, or the model is down or timed out | raise `maxConcurrent` or the model's capacity; check the endpoint |
| 502 `ai-draft-invalid` | the model's reply broke the purpose rules twice | try a clearer description or a larger model |
