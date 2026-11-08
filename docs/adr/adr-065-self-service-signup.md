# ADR-065: Organisations apply for themselves

- **Status:** Accepted
- **Date:** 2026-11-08
- **Issue:** #108

## Context

Only platform staff could register a fiduciary (`POST /api/df-reg/dfs`), so every organisation that wanted to use the
consent manager had to be onboarded by hand. Letting organisations apply for themselves puts a public, unauthenticated
write on the platform. That invites abuse: applications made in someone else's name, floods of junk, and probing for which
addresses are already customers.

## Decision

1. **Applying is public; becoming a fiduciary is not.** `POST /api/df-reg/signup` takes the organisation's name, a contact
   address and optionally its legal name, industry and website. It creates nothing but an *application*. Staff still decide,
   with the existing Activate, or a new **Reject** with a reason.
2. **The contact address is proved first.** The application mails a link to that address (`#/signup/verify?id=…&token=…`, a
   page of the DF Console that needs no sign-in). The token is 256 random bits, only its SHA-256 is kept, it works **once**
   and for **24 hours**, and a wrong token, a wrong id, an expired or a used link are refused in the same words
   (`signup-invalid-token`). Nobody can apply in another's name and have them contacted as a customer.
3. **Confirming creates the fiduciary** exactly as staff registering one does: `PENDING`, on the FREE plan (a paid plan is
   chosen with staff afterwards), marked as self-service (`signedUpAt`) so staff can tell it from one they onboarded. The
   applicant is told it is with the team, and staff are emailed (`cm.dfreg.usage-alerts.platform-emails`). Nobody is the
   organisation's Owner, and nobody can sign in, until staff activate it.
4. **Staff reject** an application that is still waiting (`POST /api/df-reg/dfs/{id}/reject`, administrators): the fiduciary
   becomes `REJECTED` (final), and its contact is emailed the reason. A rejected application can't be activated.
5. **No enumeration.** A valid application gets the same `202` and message whether or not the address already belongs to a
   fiduciary. Only someone who holds the mailbox, by using the link, is told `df-already-registered`.
6. **Held to account**, in two places:
   - **df-reg**: one address is held to **3 applications a day** and the whole platform to **200 a day**
     (`signup-rate-limited`, 429; `cm.dfreg.signup.*`).
   - **The gateway** (`SignupRateLimitGlobalFilter`): one client is held to **10 requests an hour** across the two public
     calls, in a Redis counter. The client is the connection's address; `X-Forwarded-For` is believed only when
     `cm.rate-limit.trust-forwarded-for` is set, for a deployment whose proxy the platform controls, since anyone else
     could write any value there and so avoid the limit. It fails open if Redis is unreachable (df-reg's limits remain).
7. **Unconfirmed applications are deleted** a week after their link expired (hourly job): they hold an address nobody proved.
8. **Audited**: `tenant.signed-up` when confirmed, `tenant.rejected` with the reason.

## Also fixed on the way

Activating a fiduciary that was not `PENDING` answered 500, and, worse, provisioned its API client before failing, which
would have replaced the client secret of one already active. It now answers `409 df-cannot-activate` before touching
anything.

## Not yet

- **A CAPTCHA or proof of work.** The address proof, the per-address and per-client limits and the daily cap bound the abuse
  but a determined attacker with many addresses can still make staff read junk. Add one if it happens.
- **Checking the organisation is real** (a registry look-up of its registration number, ADR-029 §1) is still staff's manual
  check; the form does not ask for a registration number.
- **A plan choice on the form.** Applicants are on FREE; staff change it.
- **Applicants cannot see the state** of their application; they are told by email.
- **The Keycloak invitation** at activation is the existing first-Owner invitation (ADR-033).

## Options considered

- **Create the fiduciary on submit, confirm later:** simpler, and leaves unverified fiduciaries (and their addresses) in the
  staff list for anyone to create.
- **Let people apply without proving the address:** then anyone can have the platform email anyone, and the staff list fills with
  junk. The proof is cheap and removes both.
- **Self-activation after the address is confirmed:** an organisation would be live with no one having looked at it; the
  platform processes personal data for its fiduciaries, so a person decides (ADR-029).
