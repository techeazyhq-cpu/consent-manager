# ADR-062: One error dictionary, used by every service

- **Status:** Accepted
- **Date:** 2026-11-07
- **Catalogue:** [docs/errors/error-dictionary.md](../errors/error-dictionary.md), generated from the dictionary.

## Context

The platform answers errors in the RFC 9457 `problem+json` form with a `type` of `urn:cm:problem:<name>`, but nothing
held the names together. Reading the code for this decision found:

- **115 distinct problem codes** in the source, **18** of them mentioned in any document. The other 97 existed only as
  string literals in handlers, so no one could tell a caller what a code meant, what to do about it, or whether trying
  again would help.
- **Three ways to answer**: a business-rule exception turned into a problem by `cm-web-kit`; the same handler written
  again in `cm-res-kit` for an unavailable service; and hand-built JSON in the gateway's filters and the audit
  handlers, whose `title` was the code in some places, `DENY` in one, and the HTTP phrase `Too Many Requests` in another.
- **A code with two statuses** (`df-not-active` was 403, 404 and 409; `no-draft` was 404 and 409), so a caller could not
  write logic against it.
- **A hidden coupling**: the gateway took the `title` of the problem from `cm-df-reg-svc` to be its code, and the
  scripted security checks and the DF Console compared `title` to a code. A better title would have broken them quietly.
- **No catalogue of warnings.** A log line an operator should act on, such as an account erasure that gave up, was free text.
- **Nothing between 400 and a crash.** Input that couldn't be read, or an exception nobody planned for, got Spring's
  default error JSON, with a timestamp and an internal request id.

## Decision

1. **One dictionary**, `backend/libs/cm-web-kit/src/main/resources/cm-error-dictionary.json`, with an entry for every
   API problem, operational log code and event. For a problem: its stable `code`, `status`, `category`, a short human
   `title`, what it `description` means, the `action` the caller should take, whether it is `retryable`, and which
   services raise it. A log code has a `level` instead of a status.
2. **One way to answer.** `Problems` builds every error response from the dictionary, and the gateway, the audit
   service, the circuit-breaker handler and the business-rule handler all use it:
   `{"type":"urn:cm:problem:quota-exceeded","code":"quota-exceeded","title":"Monthly allowance used up","status":429,
   "detail":"...","retryable":true,"correlationId":"..."}`. The `code` (also the end of `type`) is the stable name to
   write logic against; the `title` is the same words every time for a code; the `detail` says what was wrong this
   time; `correlationId` is the `X-Correlation-Id` the response carries.
3. **`title` is now words for people, not the code.** This changes what callers see: anything that compared `title` to a
   code must use `code` (or the end of `type`) instead. The gateway, the DF Console and the scripted checks were
   changed; an integration written against the old `title` needs the same change. `type` has not changed.
4. **A fallback for what no one answers** (`FallbackProblemWebExceptionHandler`): input that can't be read is `400
   malformed-request`, and an unexpected exception is `500 internal-error`. Neither shows anything about the cause:
   not the parser's words and not the exception. The exception is logged as `[CM-INTERNAL-ERROR]` with the correlation id
   the caller was given. Other `ResponseStatusException`s (an unknown route, the wrong method), Spring Security's
   exceptions and a client that went away are left alone.
5. **A name for the warnings an operator acts on.** An operational log code is `CM-` and upper-case words, written in
   square brackets at the start of a WARN or ERROR message: `[CM-ERASURE-STUCK]`. The first set is the ones with a
   runbook or an alert: an erasure step failing or stuck, an outbox relay pass or a usage alert check failing, an
   unexpected error ending a request, and a problem missing from the dictionary. The Keycloak event
   `cm_otp_rate_limited` is listed as an event.
6. **Held to it in CI.** `scripts/error-dictionary.mjs --check` reads the sources and fails when:
   - a code is raised but not listed;
   - a code is listed but raised nowhere;
   - a code is raised with a status other than the dictionary's;
   - `urn:cm:problem:` is written anywhere but `Problems`, so a problem is built by hand;
   - an entry is malformed (empty text, a title over 60 characters, a status that doesn't fit its category,
     a service that doesn't exist);
   - the catalogue page is out of date (`--write` regenerates it).
   At runtime a code that is somehow unlisted still gets an answer, with a title made from its name, and is logged once
   as `[CM-ERRDICT-UNLISTED]`; it never breaks a request.
7. **Names and statuses don't change once released.** A code keeps its meaning and its status. If two situations need
   different statuses they are two codes. Titles, descriptions and actions can improve at any time.
8. **Corrections made on the way**, because one name with three statuses can't be written against:

   | Was | Now |
   |---|---|
   | `df-not-active` 404, when raising a request or a batch for a fiduciary that isn't active | `df-not-active` **403**, like the API-key check |
   | `df-not-active` 409, when adding team members before activation | `df-activation-required` 409 |
   | `no-draft` 404, when reading a purpose's draft that doesn't exist | `draft-not-found` 404 |

9. **One correlation id per call.** An error quotes the call's correlation id, and found that through the gateway a
   response carried two `X-Correlation-Id` values: the gateway made one and never passed it on, and the service made
   another. `CorrelationIdWebFilter` now passes its id to whatever the call goes on to call, so the gateway and the
   service share one; sends exactly one value back, whoever else set it; and takes an id from the caller only if it is a
   plain token (letters, digits, `.`, `_`, `:`, `-`, at most 64), since it now appears in response bodies and log lines.

## Not yet

- **Authentication and authorization failures** from Spring Security's entry points (an expired token, a missing
  role) still answer a bare 401 or 403 without a body. They have no dictionary entry because nothing raises one yet.
- **Other warnings.** Only the operational warnings with a runbook or an alert have codes. The rule going forward is
  that a WARN or ERROR an operator should act on gets one, and is added with it.
- **Translation.** Titles are English. Codes are language-neutral, so an app can show its own words for them.
- **The OpenAPI contract** describes the problem shape and links the catalogue, but does not list which codes each
  operation can return.
- **Other vocabularies**: webhook message types ([AsyncAPI](../api/df-webhooks.asyncapi.yaml)), audit action names,
  and notification-service delivery reasons are their own lists.

## Options considered

- **A Java enum of codes:** compile-time safe, but the gateway filters, the scripts, the documentation and any
  non-Java reader need the same data, and an enum in one module is not visible to the others without a new dependency.
  A data file read by a small class gives them all one source.
- **The OpenAPI file as the source:** it describes the HTTP API only; log codes and events would have no home, and
  the audit and gateway codes are not all in it.
- **Keeping `title` as the code and adding the words elsewhere:** nothing breaks, but the field would stay at odds
  with RFC 9457, where a title is a human summary of the problem type.
- **Failing a request that raises an unlisted code:** a documentation mistake would become an outage; CI is the right
  place to stop it.

## Consequences

- A caller can look up any error, know whether to retry, and quote one correlation id; support can find the log line
  by it.
- Adding an error means adding its entry, which is the moment someone writes down what it means and what to do.
- A change that was a one-word edit in a handler now touches the dictionary and the generated page.
- Integrations that compared `title` to a code break (see 3); the platform's own were updated here.
