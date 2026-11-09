# ADR-101: Changing the sign-in service key without a gap

- **Status:** Accepted
- **Date:** 2026-11-09
- **Issue:** #105
- **Completes:** the "no rotation without a gap" limit of [ADR-083](adr-083-key-for-requesting-a-sign-in-code.md)

## Context

ADR-083 made `POST /internal/otp/send` need a key, the same value in cm-ntf-svc (`cm.ntf.internal-api-key`) and in Keycloak's sign-in
step. A secret that cannot be changed safely does not get changed: with one value in two places, any change leaves a window in which one
side has the new key and the other the old, and a sign-in in that window fails. A key that has leaked, or belonged to someone who has
left, then stays.

## Decision

1. **A second, previous key.** `cm.ntf.internal-api-key-previous` (environment `CM_NTF_INTERNAL_API_KEY_PREVIOUS`). While it is set, a
   request carrying either the current key or the previous one is accepted; anything else is refused as before. Compared as digests of
   equal length, like the current key.
2. **It is counted.** Each request that is accepted only because it carries the previous key increments
   `cm.ntf.internal.key.previous.used` (present at zero from the start), so the operator can see when nothing sends the old key any
   more. A warning at start-up says that a previous key is set.
3. **The change is four steps, with no refusal at any point:**
   1. Set `NTF_INTERNAL_API_KEY_PREVIOUS` to the old value and `NTF_INTERNAL_API_KEY` to the new one in the secret; restart the
      notification service. It accepts both.
   2. Set the new value as Keycloak's `NTF_API_KEY`, and restart Keycloak.
   3. Wait until `cm_ntf_internal_key_previous_used_total` has stopped rising (a sign-in after the restart uses the new key).
   4. Remove `NTF_INTERNAL_API_KEY_PREVIOUS` from the secret and restart the notification service. The old key is refused.
4. **Helm** passes the previous key from the secret key `NTF_INTERNAL_API_KEY_PREVIOUS`, optional (a cluster without it starts as
   before, with no previous key).
5. **A previous key with no current key opens nothing:** with no current key the endpoint is open as ADR-083 left it, and the previous
   means nothing.

## Checked

- Unit tests: both keys accepted and a third refused; the previous key counted and the new one not; a previous key alone changes
  nothing; once the previous is removed the old key is refused.
- Live, on the rebuilt notification service (image built from this change) with `CM_NTF_INTERNAL_API_KEY=new-key-1` and
  `..._PREVIOUS=old-key-0`: a request with the new key and one with the old key both got past the filter (the handler then answered 500
  to my empty `{}` body, which is the handler, not the filter); a third key and no key got 401. The previous-key counter rose once per
  old-key request and not for the new key, `cm_ntf_otp_send_refused_total{reason="key"}` counted the two refusals, and the start-up
  warning was logged.

## Not yet / limits

- **One shared secret still,** not a key per caller (ADR-083).
- **The counter shows use, not who:** it says the old key is still being sent, not by what. Find the sender in the network and the
  service log before removing it, if the count does not fall after Keycloak has the new key.
- **No alert:** a rotation is a deliberate act by the operator, who watches the counter; a key left as "previous" indefinitely is only
  visible by its warning at start-up and the secret itself.
- **Keycloak's key still has to be changed by hand** (it is a server setting, not realm data, ADR-083); the sequence above is the
  procedure, not something a command does for you.
- **Still optional:** making the key required by default is separate (ADR-083).
- ~~**An empty JSON body to the endpoint gave a 500, not a 4xx.**~~ Fixed in the change that follows this record: a missing body, or a
  body without a channel, a destination or a code, is a 400 `otp-send-invalid`, and nothing is sent.
