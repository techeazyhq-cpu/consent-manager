# ADR-114: Drafting with a model is turned on per tenant, at onboarding or when the tenant asks

- **Status:** Accepted
- **Date:** 2026-11-12
- **Builds on:** [ADR-111](adr-111-ai-assisted-purpose-drafting.md), [ADR-113](adr-113-ai-model-provider-settings-in-cm-admin.md); follows the shape of plan change requests ([ADR-039](adr-039-billing-visible-to-fiduciaries-plan-change-requests.md))

## Context

ADR-113 let the platform's administrators choose the model. Once that model is a vendor's, every description a fiduciary types goes to a third party and
costs money, so it should not be on for every tenant by default. The operator wants drafting to be a choice made for each tenant: ticked when the tenant
is onboarded, or asked for afterwards by the tenant.

## Decision

1. **Off for a tenant until staff turn it on.** The platform having a model (ADR-113) is necessary but not enough. A tenant that has not been given access
   gets `403 ai-drafting-not-enabled` from `POST /prps-defs/draft-assist`, **before** the description is screened or the model is called.
2. **At onboarding.** CM Admin's *Onboard tenant* has a *Draft purposes with the AI model* box, off by default, with a plain note of what it sends and that
   the tenant can ask later. When ticked, access is turned on after the tenant is registered and activated; if that last step fails, the tenant is kept and
   the page opens it with a notice, as for a logo that failed to upload.
3. **After onboarding, by request.** In the DF Console's purpose editor, a tenant without access (when the platform has a model) sees *Draft with AI*
   with **Ask to turn it on** and an optional note (500 characters). People who manage purposes may ask. A tenant has **at most one request waiting**; it can
   **withdraw** it; a declined tenant sees the reason and may ask again. Nothing is offered when the platform has no model.
4. **Staff decide.** On CM Admin's *AI drafting model* page, *Organisations that can draft* lists requests waiting (with the note) and who it is on for.
   - Any staff may **turn it on** (an operator onboards tenants, so an operator can tick the box) and may turn it on for a tenant already onboarded.
   - **Administrators** approve or decline a request (a decline needs a reason, which the tenant sees) and turn it **off**, as with a plan change.
5. **One row per tenant** (`cns_cmd.ai_drafting_access`) holds the state and the latest request, so two requests cannot be waiting. The history of who asked,
   decided and when is the audit trail: `ai-drafting.access-requested`, `-withdrawn`, `-granted`, `-approved`, `-declined`, `-revoked`, each with the
   tenant and the actor. **The note and the reason are never put in the audit trail**; they are the tenant's and staff's words to each other.
6. **The status the DF Console reads** says `enabled` (the platform has a model *and* this tenant may use it, so existing clients keep working),
   `modelOffered`, `access` (`ENABLED`, `PENDING`, `DECLINED`, `NOT_ENABLED`) and a decline reason.
7. **Where it lives.** Entitlement is in cns-cmd, which enforces it, not in df-reg or billing: it is a property of how a tenant may use one feature, and
   enforcing it needs no call to another service. Onboarding stays a sequence of calls from CM Admin, as with the logo and activation.

## Consequences

- Existing tenants lose drafting until staff turn it on: the migration creates no rows, so access is off. (The feature was days old and off by default.)
- Self-service signups (ADR-065) have no staff step, so they begin without drafting and ask later like any tenant.
- Turning a tenant off takes effect on its next draft; a draft already being written is not interrupted.
- Not a billing feature: no price, no payment. If drafting is later charged for, access could depend on a plan.
- A decision made by one staff member on a request is final in this record; the earlier request's text is overwritten by a new one. The audit trail keeps
  that it happened, not what was said.
