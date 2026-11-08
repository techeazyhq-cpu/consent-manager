# ADR-066: How a principal hears of new consent requests

- **Status:** Accepted
- **Date:** 2026-11-08
- **Issue:** #106

## Context

A principal learns of a consent request in one of three ways: it is on their dashboard; if they turned it on, a push goes to
their devices; and, when an organisation addressed the request to their email or phone, the notice is mailed or texted to
that address. The only choice a principal had was to remove a push device. They could not stop an organisation's emails, and
could not pause push without removing every device.

## Decision

1. **Two switches, both on until the principal changes them** (`GET`/`PUT /api/ntf/preferences`, a principal only):
   - **Push**: pausing it stops every device, which stay registered.
   - **Request emails**: organisations' emails of a request to the principal's address. Turned off, a request addressed to that
     address is **not mailed**; it waits on the dashboard, and is there when the principal opens the portal (opening it claims
     what was addressed to them).
2. **What can't be turned off**: the dashboard (a request is always there to decide or ignore) and **sign-in codes**, which
   are security, not marketing. Text messages are not affected by the email choice.
3. **The address is never kept.** The email choice is tied to the address the principal signed in with, which the sign-in
   code proved is theirs, read from their verified token and never typed. Only a **keyed hash** (HMAC-SHA256,
   `cm.ntf.email-fingerprint-key`) of it is stored, enough to recognise the address a request is sent to, and not testable
   against a list of addresses without the key. The page shows the address back masked (`a•••@example.com`).
4. **Someone who signs in with a mobile number has no email choice** (`email-not-available`, 409, if they try to turn emails
   off): there is no address of theirs to stop emails to.
5. **The caller is always the token.** A `dpId` in a body is ignored; staff and anonymous callers have no preferences.
6. **Closing an account erases them** (the notification step of ADR-059 deletes the row, and answers with the count).
7. **The organisation is not told.** A request that was not mailed stays `PENDING`, as one nobody has answered; nothing says
   the principal turned emails off, since that would tell organisations who has opted out.

## Deployment

The chart passes `NTF_EMAIL_FINGERPRINT_KEY` to cm-ntf-svc as a **required** secret, with no default (`values-*.yaml` map it to each
cloud's secret store, and the deploy README says how to make one). The default in the code is for the local stack only.

## Not yet

- **A phone preference**: the same for text messages, once a real SMS gateway exists (#103).
- **Per organisation** (mute one organisation, allow another) and **quiet hours**.
- **A principal who never signed in has no choice to make**, and organisations can still email them: there is nobody to ask.
  This is the point of the sign-in: the choice becomes theirs once they have an account.
- **Email addresses that differ only in a `+tag`** are different addresses to this feature, as they are to sign-in (ADR-061
  treats them as one mailbox for abuse limits; preferences do not).
- **Rotating the fingerprint key** invalidates every stored hash, so principals' email choices stop applying until they save
  again. There is no re-keying procedure yet.

## Options considered

- **Store the address with the preference:** simplest, and puts every opted-out principal's address in a second table that has
  to be protected, erased and exported. A keyed hash recognises the address without keeping it.
- **An unkeyed hash:** the table could then be tested against a list of addresses to learn who opted out. The key prevents it.
- **Tell the organisation the principal opted out:** useful to them, and leaks the choice.
- **Let the principal disable sign-in codes or the dashboard:** they would lock themselves out of the very thing the emails
  point to.
