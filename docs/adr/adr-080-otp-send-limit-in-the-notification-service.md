# ADR-080: The notification service limits the sign-in codes it sends

- **Status:** Accepted
- **Date:** 2026-11-08
- **Issue:** #105

## Context

ADR-061 put limits on sign-in codes in Keycloak: codes to one address, from one network address, and wrong guesses. Its "Not yet"
list named two things this ADR takes: a limit **in cm-ntf-svc**, "which is where the SMS is paid for, and so protects against a
caller other than Keycloak", and **persistence across a full Keycloak restart**, since those counts are in a Keycloak cache in
memory. cm-ntf-svc's `/internal/otp/send` is reachable by anything on the private network, and trusts it. A misconfigured
caller, a second sign-in provider, or a Keycloak that was restarted mid-attack can each send a person any number of codes.

## Decision

1. **cm-ntf-svc keeps a ledger of the codes it was asked to send** (`ntf.otp_send`, in its own database, kept two days) and refuses
   one that would pass a limit: per destination in an hour (default 20) and in a day (default 40), and per channel in an hour
   (default 1000, the most the platform is willing to spend on SMS or email in an hour whoever asks). 0 switches a limit off.
   They are deliberately looser than Keycloak's (5 an hour, 10 a day, ADR-061): this is the backstop that holds when the first
   line does not, not a second place to be refused by.
2. **The destination is a keyed hash** (HMAC-SHA256 with the key of ADR-066), never the address or number, and is normalised as the
   sign-in does: the `+tag` of an email is ignored, a phone number is its digits. The same address by email and by SMS are two
   destinations.
3. **A refusal is 429 `otp-send-limited` with `Retry-After`,** and the sign-in treats any failure of this call as "could not send"
   and gives the person's allowance back (ADR-061 decision 5). Nothing says which limit was reached.
4. **A code that was not sent does not count.** The place is taken before the send and given back if delivery fails.
5. **Survives restarts of everything:** the ledger is in PostgreSQL, so neither a Keycloak restart nor a cm-ntf-svc restart forgets it.
6. **Local stack:** `docker compose` loosens the per-destination limits (1000 an hour, 5000 a day) because the test kit signs in
   as the same few addresses all day. They are environment variables of cm-ntf-svc (`CM_NTF_OTP_LIMITS_*`), not a list of exempt
   addresses, so there is nothing to forget to remove: a deployment gets the defaults unless it sets them.

## Not yet / limits

- **Concurrent requests can pass a limit by the number in flight together.** The count and the new row are one statement, but
  PostgreSQL's default isolation lets two statements started at the same instant both see the same count. For a backstop that is
  fine; for exactness it would need a lock per destination.
- **Wrong guesses are still counted only in Keycloak (in memory).** Persisting those is the other half of "survives a Keycloak
  restart"; the guess counts live where the code is checked, not here.
- **Telling the account's owner** that codes were requested or refused for their address, **device fingerprinting, anomaly
  detection and CAPTCHA** remain as ADR-061 left them: decide from what the `cm_otp_rate_limited` events show.
- **No alert on refusals yet:** the refusal is logged by the sign-in as a failed send, but there is no metric on `otp-send-limited`.
  A counter for it is a small next step with the metrics of ADR-070.
- **Who may ask** is decided separately: [ADR-083](adr-083-key-for-requesting-a-sign-in-code.md) puts a key on the endpoint.
- **Not per-caller:** the ledger does not know which service asked, only who it was for.

## Options considered

- **Redis:** cm-ntf-svc does not use it, and a ledger that must survive restarts wants the database it already has.
- **The same limits as Keycloak's:** two places refusing for the same reason, with the second hiding the first.
- **Authenticating `/internal/otp/send`:** worthwhile and separate (a service credential between Keycloak and cm-ntf-svc); the
  network policy is the control today. A limit helps even when the caller is who it should be.
