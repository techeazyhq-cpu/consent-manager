# ADR-061: Limits on sign-in codes across sign-ins

- **Status:** Accepted
- **Date:** 2026-11-07
- **Implements:** epic #105 (OTP anti-abuse hardening), for rate limits and guess limits;
  [ADR-008](adr-008-identity-keycloak-and-passwordless-sign-in.md) chose the one-time code, and left its own limits to
  the sign-in session.

## Context

Everyone signs in with a six-digit code sent to an email address or a phone number: staff, fiduciaries' people and data
principals alike. A code lives 5 minutes and allows 5 wrong guesses, and a new one can be sent after 30 seconds, at most
3 times. Those limits are kept in the Keycloak sign-in session, so each starts again with every sign-in page. Two holes
followed:

1. **Anyone could make the platform send codes to any address or number, without limit.** Each fresh sign-in page
   allows three more. That is harassment of the person who owns the address, and a cost: an SMS is paid for.
2. **A fresh sign-in gave a fresh code and fresh guesses.** Five guesses against a million codes is 5 in a million, but
   for as many codes as the first hole allowed. Keycloak's brute-force protection helps only for an account that
   already exists, and a data principal signs up by signing in, so for a new address nothing counted the guesses at all.

Code generation and storage were already sound: a `SecureRandom` code, kept only as a salted hash, compared in
constant time.

## Decision

Limits that hold across sign-ins, realms and Keycloak nodes (`OtpAbuseGuard`), in addition to the session's own:

| Limit | Default | Counted |
|---|---|---|
| Codes sent to one address | 5 an hour, 10 a day | when a code is requested |
| Codes requested from one network address | 300 an hour | when a code is requested |
| Wrong codes for one address | 10 an hour, 20 a day | when a wrong code is entered, whether or not the address has an account |

1. **Where they live.** Keycloak's single-use object cache, which every node shares. Each event claims one numbered slot
   with an atomic put-if-absent, and the slot expires after its window. That is an exact sliding window: two nodes
   racing can't both take the last slot. Nothing else is deployed.
2. **What an address is.** A hash, so no address is in the cache, shared by every realm (the sandbox and production
   count together). For email, the `+tag` before the `@` is ignored, because `a+1@x.com` and `a+2@x.com` are one
   mailbox: a per-address limit that ignored that would be bypassed with one character.
3. **Wrong guesses stop everything for that address.** Once the budget is spent, no code is sent and no code is
   accepted for the address, **not even the right one**. A limit that still checked the right code would only slow
   guessing down. The cost is that someone can lock a victim's address for up to an hour by entering wrong codes for it;
   Keycloak's own brute-force protection has the same property. The windows are sized so a person making typing
   mistakes never meets them, and the day budget bounds a patient attacker to 20 guesses a day: about 0.7% over a year
   of non-stop effort against one address, where before there was no bound.
4. **Same words for everyone.** The limits ignore whether an address has an account, and the refusal is the same page
   in the same words, so a refusal can't be used to find out who is registered.
5. **A code that wasn't sent is given back.** If cm-ntf-svc refuses the destination or is down, the allowance claimed
   for that request is released, so an outage doesn't use up people's codes. A refusal gives back everything it had
   claimed from the other limits too.
6. **The network limit is generous on purpose.** Mobile carriers and offices put many real people behind one address,
   and the limits per address are what protect a person; this one only bounds what one host can spend. It uses the
   address Keycloak resolves, so it depends on `KC_PROXY_HEADERS=xforwarded` behind an ingress, which the Helm chart
   sets. A client that can forge that header can evade this limit, never the limits per address.
7. **Auditable.** A refusal logs its kind (never the address) and raises a Keycloak event `cm_otp_rate_limited` with
   `cm_otp_limit` as `TOO_MANY_CODES`, `NETWORK_BUSY` or `TOO_MANY_WRONG_GUESSES`, so an alert can be set on it.
8. **Configurable.** The authenticator's settings in each realm: `sendsPerAddressPerHour`, `sendsPerAddressPerDay`,
   `sendsPerNetworkPerHour`, `wrongGuessesPerAddressPerHour`, `wrongGuessesPerAddressPerDay`. Each is a whole number;
   0 switches that limit off.

## Not yet

- **Telling the account's owner** that codes were requested or guesses refused for their address.
- **Device fingerprinting, anomaly detection, CAPTCHA or proof of work**, which the epic names. The limits above remove
  the cheap abuse; whether the rest is worth its cost to honest people should be decided from what the event shows.
- **A limit in cm-ntf-svc**, which is where the SMS is paid for, and so protects against a caller other than Keycloak.
  Nothing else calls its `/internal/otp/send` today.
- **Persistence across a full Keycloak restart.** The cache is in memory; a restart of every node forgets the counts.
  A rolling restart keeps them.

## Options considered

- **Counters in Redis or cm-ntf-svc:** cm-ntf-svc can't see wrong guesses, which only Keycloak verifies; Redis would
  be a new dependency for Keycloak's sign-in. The cache Keycloak already shares is enough.
- **Keycloak's brute-force protection alone:** it needs a user, and a data principal's address has none until it
  signs in.
- **A counter incremented with get and replace:** not atomic across nodes, and a replace can drop the entry's expiry.
  One key per event, claimed with put-if-absent, is.
- **Locking out on wrong guesses but still checking a right code:** no stop at all, as above.

## Consequences

- Bombing an address stops at 5 codes an hour, and guessing one at 20 a day; before, neither was bounded.
- A person who really can't receive their code (spam folder, wrong number) stops after 5 in an hour and has to wait.
  The refusal says so and mentions the spam folder.
- Each code request makes up to 6 cache calls, and each check of the guess budget up to 20.

## Compliance impact

- DPDPA Rule 6 (reasonable security safeguards) and the equivalent security duties of GDPR Art. 32: protecting the
  sign-in that guards the principal's consents against guessing and abuse.
