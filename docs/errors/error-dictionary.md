<!-- Generated from backend/libs/cm-web-kit/src/main/resources/cm-error-dictionary.json by
     node scripts/error-dictionary.mjs --write. Do not edit by hand: edit the dictionary. -->

# Error dictionary

Every error, warning and event this platform raises, in one place ([ADR-062](../adr/adr-062-error-dictionary.md)).
123 entries: 115 API problems, 7 operational log codes and 1 event.

## How an API error looks

An error response is `application/problem+json` ([RFC 9457](https://www.rfc-editor.org/rfc/rfc9457)):

```json
{
  "type": "urn:cm:problem:quota-exceeded",
  "code": "quota-exceeded",
  "title": "Monthly allowance used up",
  "status": 429,
  "detail": "DF 6f1c… is not eligible to raise a new consent request",
  "retryable": true,
  "correlationId": "0b1e7c7a-6f43-4f4e-9d52-8d2a1f4c7a10"
}
```

- **`code`** (also the end of `type`) is the stable name: write your logic against it, never against `title` or `detail`.
- **`title`** is the same short, human sentence every time for a code, from this dictionary.
- **`detail`** says what was wrong this time (which field, which id). It can change; do not parse it.
- **`retryable`** says whether trying the same request again later can work. Honour `Retry-After` when present.
- **`correlationId`** is also the `X-Correlation-Id` header. Quote it when you report a problem.

## Naming

- API problem codes are lower-case words joined by hyphens, and never change once released: `quota-exceeded`.
- Operational log codes are `CM-` and upper-case words, written in square brackets at the start of the message so an alert can find them: `[CM-ERASURE-STUCK]`.
- Events are lower-case words joined by underscores, as Keycloak names its own: `cm_otp_rate_limited`.
- A code has one status. If two situations need different statuses, they are two codes.

## Adding one

Add the entry to the dictionary and raise it from the code (`new DomRuleViolation(status, "code", detail)`, `Problems.body("code", ...)`, or `[CM-...]` in a log message); then run `node scripts/error-dictionary.mjs --write`. CI refuses a code that is raised but not listed, a listed code nobody raises, a status that differs from the dictionary's, a problem built by hand, and a stale copy of this page.

## API problems

### Validation

| Code | Status | Title | What it means | What to do | Retry | Raised by |
|---|---|---|---|---|---|---|
| `ambiguous-credentials` | 400 | Two kinds of credentials sent | The request carried both an API key and a bearer token, so it is unclear which identity is calling. | Send one credential: either the API key headers or a bearer token. | no | gw |
| `confirmation-required` | 400 | Confirmation needed | Closing an account needs the confirmation words typed exactly. | Type the words the page asks for. | no | dsr |
| `invalid-answer` | 400 | Answer not valid | An answer needs a reply, and a refusal also needs its legal ground. The detail says which. | Add what is missing. | no | dsr |
| `invalid-api-key` | 400 | API key request not valid | The request to issue an API key was refused. The detail says why. | Correct the request as the detail says. | no | df-reg |
| `invalid-batch` | 400 | Batch not valid | The batch request was refused. The detail says why. | Correct the batch as the detail says. | no | cns-cmd |
| `invalid-change` | 400 | Change not valid | The change to the consent or purpose was refused. The detail says why. | Correct the change as the detail says. | no | cns-cmd |
| `invalid-count` | 400 | Count out of range | The number of requests to reserve or release must be from 1 to 100,000. | Send a count in range. | no | bil |
| `invalid-decision` | 400 | Decision not valid | A decline needs a reason, or the note was too long. | Say why the request is declined, in the note. | no | bil |
| `invalid-df-id` | 400 | Fiduciary id not valid | A fiduciary id must be a UUID. | Use the id as given by the platform. | no | df-reg |
| `invalid-df-profile` | 400 | Fiduciary profile not valid | A field of the fiduciary's profile was refused. The detail names it. | Correct the profile field named in the detail. | no | df-reg |
| `invalid-dp-request` | 400 | Request not valid | The request is missing its consent or details, or they are too long. The detail says which. | Correct the request as the detail says. | no | dsr |
| `invalid-grant` | 400 | Choice not valid | The data or validity the person chose is not allowed for this request. The detail says why. | Choose within what the request offers. | no | cns-cmd |
| `invalid-kind` | 400 | Kind of request not recognised | The kind of request must be RELAY or GRIEVANCE. | Use one of them. | no | dsr |
| `invalid-list` | 400 | Recipient list not valid | The recipient list could not be read. The detail says why. | Correct the list as the detail says. | no | cns-cmd |
| `invalid-logo` | 400 | Logo not valid | The logo is not a supported image. | Upload a PNG, JPEG or SVG logo. | no | df-reg |
| `invalid-member` | 400 | Team member not valid | A required field of the new member is missing or not valid. The detail names it. | Correct the field named in the detail. | no | df-reg |
| `invalid-operator` | 400 | Operator not valid | A required field of the new operator is missing or not valid. | Correct the field named in the detail. | no | stf |
| `invalid-parameter` | 400 | Parameter not valid | A query parameter has the wrong form. The detail names it. | Correct the parameter. | no | cns-cmd |
| `invalid-period` | 400 | Month not valid | A month is written like 2026-11. | Use that form. | no | bil |
| `invalid-plan-change-request` | 400 | Plan change request not valid | The request names no plan, the plan the fiduciary already has, or a note that is too long. | Choose a different plan and keep the note short. | no | bil |
| `invalid-purpose` | 400 | Purpose not valid | The purpose or its content was refused. The detail names what is wrong. | Correct what the detail names. | no | cns-cmd |
| `invalid-request` | 400 | Request not valid | The request is missing something it needs, or a value is not valid. The detail says which. | Correct the request as the detail says. | no | cns-cmd, df-reg |
| `invalid-right` | 400 | Right not recognised | The right asked for is not one of access, correction, erasure, grievance or nomination. | Use one of the listed rights. | no | dsr |
| `invalid-sts` | 400 | Status not recognised | The status filter is not one of the values this list has. | Use one of the listed values. | no | bil, dsr |
| `invalid-webhook` | 400 | Webhook endpoint not valid | The endpoint's address, its message types or the request body was refused. The detail says why. | Correct the endpoint as the detail says. | no | cns-cmd |
| `list-too-large` | 413 | Recipient list too large | The file is larger than 5 MB or has more recipients than a batch allows. | Split the list. | no | cns-cmd |
| `logo-too-large` | 413 | Logo too large | The logo is larger than allowed. | Upload a smaller image. | no | df-reg |
| `malformed-request` | 400 | Request could not be read | The body, a parameter or a header could not be understood: for example JSON that does not parse, or a value of the wrong type. | Check the request against the API description. | no | aud, bil, cns-cmd, cns-qry, df-reg, dsr, gw, ntf, stf |
| `no-valid-recipients` | 400 | No valid recipients | None of the rows in the list was a usable email address or number. | Check the list, and that its contact column is named. | no | cns-cmd |
| `not-a-test-contact` | 400 | Not a test contact | In the sandbox, requests can only go to contacts reserved for testing. | Use a test contact from the developer docs. | no | cns-cmd |
| `payload-too-large` | 413 | Request body too large | The request body is larger than the gateway accepts. | Send a smaller body, or split it. | no | gw |
| `push-token-invalid` | 400 | Push token not valid | The device token is missing or not valid. | Register the device again. | no | ntf |
| `unknown-sub-pln` | 400 | Unknown plan | There is no plan with that name. | List the plans and use one of their names. | no | bil, df-reg |
| `unsupported-list` | 415 | List type not supported | The file is not JSON, CSV or Excel. | Send the list as JSON, CSV or .xlsx. | no | cns-cmd |

### Authentication

| Code | Status | Title | What it means | What to do | Retry | Raised by |
|---|---|---|---|---|---|---|
| `api-key-invalid` | 401 | API key not accepted | The API key is missing, unknown, malformed or revoked, or its signature could not be checked. The reasons are not told apart on purpose. | Check the key, and that it was copied whole. Issue a new key in the DF Console if it was revoked. | no | df-reg, gw |
| `signature-expired` | 401 | Request signature out of date | The signed timestamp is more than five minutes from the server's clock. | Sign each request when it is sent, and keep the caller's clock correct. | no | df-reg |
| `signature-invalid` | 401 | Request signature does not match | The signature did not match the request as received: its method, path, query, body or secret differ from what was signed. | Sign exactly the bytes sent, with the key's signing secret, and check the header formats. | no | df-reg |
| `signature-required` | 401 | Request signature required | This API key requires signed requests, and the signature headers were missing or incomplete. | Send X-Signature-Timestamp, X-Signature-Nonce and X-Signature, signed as in the developer docs. | no | df-reg |

### Authorization

| Code | Status | Title | What it means | What to do | Retry | Raised by |
|---|---|---|---|---|---|---|
| `admin-only` | 403 | Administrators only | Only a platform administrator can manage staff. | Ask a platform administrator. | no | stf |
| `aud-forbidden` | 403 | Not allowed to read the audit | Only Owners, Administrators and Auditors of the fiduciary, and platform staff, read the audit. | Ask someone with that role. | no | aud |
| `consent-not-verified` | 403 | Consent not verified | The consent behind this call could not be confirmed: it is missing, withdrawn, lapsed, for another purpose or fiduciary, or the token was altered. | Do not process the personal data. Ask the person for consent again if it is still needed. | no | gw |
| `contact-not-verified` | 403 | Contact not verified | The sign-in did not verify an email address or number to claim requests with. | Sign in again with a code sent to your own address. | no | cns-cmd |
| `contact-not-yours` | 403 | That is not your address | Requests can only be claimed for the address you signed in with. | Claim requests with your own address. | no | cns-cmd |
| `dp-only` | 403 | For data principals only | Only a person has push devices. | Sign in as the person. | no | ntf |
| `may-not-answer` | 403 | Not allowed to answer requests | Only an Owner, an Administrator or a Consent manager answers requests, and platform staff answer grievances. | Ask someone with that role. | no | dsr |
| `may-not-request-plan` | 403 | Not allowed to ask for a plan change | Only an Owner or an Administrator of the fiduciary can ask for another plan, or withdraw the request. | Ask an Owner or Administrator. | no | bil |
| `not-a-data-principal` | 403 | For data principals only | Only a person signed in to the DP portal or app can do this. | Sign in as the person concerned. | no | cns-cmd, dsr |
| `not-a-fiduciary` | 403 | For fiduciaries only | The caller is not signed in as a fiduciary. | Use a fiduciary's account or API client. | no | bil, dsr |
| `not-a-fiduciary-member` | 403 | For people at a fiduciary only | This is for a person signed in to the DF Console, not for a system or API key. | Sign in to the DF Console. | no | df-reg |
| `not-permitted` | 403 | Not permitted for this caller | This action needs a person with the right role at the fiduciary, not a system or API key, or a different role. | Ask someone with the required role. | no | cns-cmd, df-reg |
| `not-platform-admin` | 403 | Platform administrators only | Only a platform administrator can approve or decline plan changes. | Ask a platform administrator. | no | bil |
| `not-platform-staff` | 403 | Platform staff only | Only platform administrators and operators can do this. | Ask platform staff. | no | bil, dsr |
| `principal-only` | 403 | For the person only | Only the person the consent is about can do this, not the fiduciary or its systems. | Ask the person to do it themselves. | no | cns-cmd |
| `role-cannot-use-sandbox` | 403 | Role cannot take sandbox keys | Only an Owner, an Administrator or a Developer can take sandbox keys. | Ask one of them. | no | df-reg |

### Subscription

| Code | Status | Title | What it means | What to do | Retry | Raised by |
|---|---|---|---|---|---|---|
| `sub-inactive` | 402 | Subscription not active | The fiduciary's subscription is suspended, so its monthly allowance cannot be used. | Contact the platform to reactivate the subscription. | no | bil |
| `subscription-inactive` | 402 | Subscription not active | The fiduciary's subscription is suspended or ended, so new consent requests are refused. | Contact the platform to reactivate the subscription. | no | cns-cmd |

### Not found

| Code | Status | Title | What it means | What to do | Retry | Raised by |
|---|---|---|---|---|---|---|
| `api-key-not-found` | 404 | API key not found | No API key with that id belongs to this fiduciary. | List the fiduciary's keys and use an id from that list. | no | df-reg |
| `aud-not-checkpointed` | 404 | Entry not anchored yet | The entry is not part of a timestamped checkpoint yet, so it has no proof. | Try again after the next checkpoint. | no | aud |
| `batch-not-found` | 404 | Batch not found | No batch with that id belongs to this fiduciary. | List the batches and use an id from it. | no | cns-cmd |
| `cns-art-not-found` | 404 | Consent not found | No consent with that id is visible to the caller. | Check the id. | no | cns-cmd |
| `cns-not-found` | 404 | Consent not found | The person has no consent with that id. | Choose one of your consents. | no | dsr |
| `cns-req-not-found` | 404 | Consent request not found | No consent request with that id is visible to the caller. | Check the id. | no | cns-cmd |
| `delivery-not-found` | 404 | Webhook delivery not found | No delivery with that id belongs to this fiduciary. | List the deliveries and use an id from it. | no | cns-cmd |
| `df-api-client-not-found` | 404 | API client not found | The fiduciary has no API client; it is created when the fiduciary is activated. | Activate the fiduciary first. | no | df-reg |
| `df-not-found` | 404 | Fiduciary not found | No fiduciary with that id exists. | Check the id. | no | df-reg |
| `df-sub-not-found` | 404 | Subscription not found | The fiduciary has no subscription yet; it starts when the fiduciary is activated. | Activate the fiduciary first. | no | bil |
| `dp-request-not-found` | 404 | Request not found | No request with that id is visible to the caller. | Check the id. | no | dsr |
| `draft-not-found` | 404 | Draft not found | The purpose has no draft. | Draft a change first. | no | cns-cmd |
| `erasure-not-found` | 404 | Erasure not found | No erasure with that id exists. | List the erasures and use an id from it. | no | dsr |
| `member-not-found` | 404 | Team member not found | No member with that id belongs to this fiduciary. | List the team and use an id from it. | no | df-reg |
| `plan-change-request-not-found` | 404 | Plan change request not found | No plan change request with that id is visible to the caller. | List the requests and use an id from the list. | no | bil |
| `purpose-not-found` | 404 | Purpose not found | The fiduciary has no purpose with that code. | List the purposes and use a code from the list. | no | cns-cmd |
| `purpose-version-not-found` | 404 | Purpose version not found | The purpose has no version with that number. | Use a version from the purpose's published versions. | no | cns-cmd |
| `push-dvc-not-found` | 404 | Device not found | The person has no such device. | List the devices and use an id from it. | no | ntf |
| `record-not-found` | 404 | Consent record not found | There is no record for that consent request. | Check the id. | no | cns-cmd |
| `staff-not-found` | 404 | Staff account not found | No platform staff account with that id exists. | List the staff and use an id from it. | no | stf |
| `sub-pln-not-found` | 404 | Plan not found | There is no subscription plan with that id. | List the plans and use one of them. | no | bil |
| `webhook-not-found` | 404 | Webhook endpoint not found | No webhook endpoint with that id belongs to this fiduciary. | List the endpoints and use an id from it. | no | cns-cmd |

### Conflict

| Code | Status | Title | What it means | What to do | Retry | Raised by |
|---|---|---|---|---|---|---|
| `account-exists` | 409 | An account with that email already exists | The email address already belongs to an account, so a second cannot be created. | Use a different address, or sign in with the existing account. | no | df-reg, stf |
| `cannot-change-admin` | 409 | Administrators are managed elsewhere | Platform administrators are managed in Keycloak, not through this service. | Change them in Keycloak. | no | stf |
| `cannot-change-self` | 409 | You cannot change your own role | People cannot change or remove their own account here. | Ask another Owner or Administrator. | no | df-reg, stf |
| `dp-request-open` | 409 | Already asked | The person already has this request open for this consent. | Wait for the answer, or withdraw the open one. | no | dsr |
| `erasure-running` | 409 | Account is already being closed | An erasure for this account is already under way. | Wait for it to finish. | no | dsr |
| `last-owner` | 409 | A fiduciary must keep an Owner | The change would leave the fiduciary without an Owner, which is not allowed. | Make someone else an Owner first. | no | df-reg |
| `owner-exists` | 409 | The fiduciary already has an Owner | A fiduciary has one Owner, who manages its team. | Ask the Owner to add people. | no | df-reg |
| `plan-change-request-pending` | 409 | A plan change is already waiting | The fiduciary already has a plan change request waiting for a decision. | Withdraw the waiting request to ask for another plan. | no | bil |
| `purpose-exists` | 409 | Purpose already exists | A purpose with that code exists already. | Draft a new version of it instead of creating it again. | no | cns-cmd |
| `purpose-version-superseded` | 409 | Purpose version replaced | A newer version of the purpose has been published since, so a request cannot name this one. | Ask for the current version. | no | cns-cmd |
| `request-replayed` | 409 | Request already used | The nonce of this signed request was used before, so it was refused as a replay. | Sign every request, retries included, with a new nonce. | no | df-reg |

### State

| Code | Status | Title | What it means | What to do | Retry | Raised by |
|---|---|---|---|---|---|---|
| `api-key-revoked` | 409 | API key already revoked | The key was revoked earlier, and a revoked key cannot be changed or used. | Issue a new key if one is needed. | no | df-reg |
| `cns-art-expired` | 409 | Consent has lapsed | The consent's validity ended. | Ask for consent again. | no | cns-cmd |
| `cns-art-not-active` | 409 | Consent is not active | The consent has been withdrawn or has lapsed, so its token cannot be refreshed. | Ask for consent again. | no | cns-cmd |
| `cns-art-not-in-force` | 409 | Consent is not in force | The consent is not in force, so it cannot be changed. | Read the consent's state first. | no | cns-cmd |
| `cns-art-not-issued` | 409 | Consent is not in force | The consent was never given, or has been withdrawn, so this cannot be done. | Read the consent's state first. | no | cns-cmd |
| `cns-req-dp-not-linked` | 409 | Request not yet linked to a person | The request was sent to an address the person has not signed in with yet, so it cannot be decided. | The person must sign in with the address it was sent to. | no | cns-cmd |
| `cns-req-not-pending` | 409 | Consent request already decided | The request was already given, refused or withdrawn. | Read its current state; raise a new request if needed. | no | cns-cmd |
| `delivery-pending` | 409 | Delivery still being tried | The delivery has not finished, so it cannot be replayed yet. | Wait until it is delivered or failed. | no | cns-cmd |
| `df-activation-required` | 409 | Activate the fiduciary first | Its team can only be managed once the fiduciary has been activated. | Activate the fiduciary, then try again. | no | df-reg |
| `df-not-active` | 403 | Fiduciary not active | The fiduciary is registered but not active, or has been suspended, so it cannot act yet. | Activate the fiduciary, or contact the platform if it was suspended. | no | cns-cmd, df-reg |
| `dp-request-closed` | 409 | Request already closed | The request was already answered, refused or withdrawn. | Read its current state. | no | dsr |
| `erasure-not-stuck` | 409 | Erasure is not stuck | Only a stuck erasure can be retried. | Read its state first. | no | dsr |
| `no-draft` | 409 | No draft to publish | The purpose has no draft, so there is nothing to publish. | Draft a change first. | no | cns-cmd |
| `plan-change-request-decided` | 409 | Plan change already decided | The request was already approved, declined or withdrawn. | Read its current state; nothing more to do. | no | bil |
| `purpose-not-published` | 409 | Purpose not published yet | The purpose has no published version to ask consent for. | Publish the purpose first. | no | cns-cmd |
| `webhook-paused` | 409 | Webhook endpoint paused | The endpoint is paused, so it cannot be sent a test. | Resume the endpoint first. | no | cns-cmd |

### Quota

| Code | Status | Title | What it means | What to do | Retry | Raised by |
|---|---|---|---|---|---|---|
| `quota-exceeded` | 429 | Monthly allowance used up | The fiduciary has used its plan's whole monthly allowance, so new consent requests are refused until the month turns. | Wait for the allowance to reset, or ask for a larger plan in the DF Console under Billing. | yes | cns-cmd |
| `too-many-open-requests` | 429 | Too many open requests | A person can have at most 20 requests open at once. | Wait for answers, or withdraw one. | yes | dsr |
| `too-many-webhooks` | 409 | Too many webhook endpoints | A fiduciary can have at most ten endpoints. | Remove one you no longer use. | no | cns-cmd |

### Rate limit

| Code | Status | Title | What it means | What to do | Retry | Raised by |
|---|---|---|---|---|---|---|
| `rate-limit-exceeded` | 429 | Too many requests a second | The fiduciary's plan allows only so many requests a second, and this call went over it. | Wait for the Retry-After interval and slow down, or ask for a plan with a higher rate. | yes | gw |

### Dependency

| Code | Status | Title | What it means | What to do | Retry | Raised by |
|---|---|---|---|---|---|---|
| `api-key-check-unavailable` | 503 | API key could not be checked | The service that checks API keys is not answering. This says nothing about whether the key is valid. | Retry after the Retry-After interval. If it keeps failing, report the correlation id. | yes | gw |
| `consent-check-unavailable` | 503 | Consent could not be checked | The policy decision point could not answer in time or is unavailable. This is not a decision. | Retry after the Retry-After interval; do not treat it as a refusal or as permission. | yes | gw |
| `dependency-unavailable` | 503 | A service is unavailable | A service this call depends on is not answering and its circuit breaker is open. The request was not attempted. | Retry after the Retry-After interval. | yes | aud, cns-cmd, df-reg, dsr, gw, ntf, stf |
| `token-unavailable` | 503 | Access token unavailable | An access token for this fiduciary could not be obtained from the identity provider right now. | Retry in a moment. | yes | df-reg |

### Internal

| Code | Status | Title | What it means | What to do | Retry | Raised by |
|---|---|---|---|---|---|---|
| `internal-error` | 500 | Something went wrong on our side | An unexpected error stopped the request. Nothing about it is shown here, to avoid giving details away; the service logged it with the correlation id. | Retry once. If it happens again, report the correlation id to the platform. | no | aud, bil, cns-cmd, cns-qry, df-reg, dsr, gw, ntf, stf |
| `sub-pln-missing` | 500 | Plan missing for a subscription | A subscription refers to a plan that no longer exists. This is a data fault, not something the caller did. | Report it to the platform with the correlation id. | no | bil |

## Operational log codes

Written as `[CODE]` at the start of a WARN or ERROR message, for alerts and runbooks.

| Code | Level | Title | What it means | What to do | Raised by |
|---|---|---|---|---|---|
| `CM-ERASURE-STEP-FAILED` | WARN | Account erasure step failed | One attempt at an erasure step failed. It is tried again every two minutes until it gives up. | No action unless CM-ERASURE-STUCK follows; check the named step's service if it repeats. | dsr |
| `CM-ERASURE-STUCK` | ERROR | Account erasure stuck | An account erasure gave up on a step after eight attempts and stopped. Part of the person's data is erased and part is not. | Find the cause for the step named in the message and retry it in CM Admin; see docs/runbooks/erasure.md. | dsr |
| `CM-ERRDICT-UNLISTED` | ERROR | Problem missing from the error dictionary | A service answered a problem whose code is not in the error dictionary, so it got a title made from its name. CI should have refused it. | Add the code to cm-error-dictionary.json and run node scripts/error-dictionary.mjs --write. | aud, bil, cns-cmd, cns-qry, df-reg, dsr, gw, ntf, stf |
| `CM-INTERNAL-ERROR` | ERROR | Unexpected error ended a request | An exception nobody planned for ended a request. The caller was answered with internal-error and the correlation id; the exception and its stack trace are in this log message. | Find the log line by the correlation id the caller quotes, and fix the cause. A burst of them is an incident. | aud, bil, cns-cmd, cns-qry, df-reg, dsr, gw, ntf, stf |
| `CM-OTP-LIMITS-EXEMPT` | WARN | Sign-in code limit lifted for some addresses | Keycloak started with addresses exempt from the limit on sign-in codes sent to one address (ADR-061). That is for the local stack, where a test kit signs in as the same accounts many times an hour. | In production this is a misconfiguration: remove spi-authenticator-cm-otp-authenticator-exempt-addresses from Keycloak's settings and restart it. | idn-otp-spi |
| `CM-OUTBOX-RELAY-FAILED` | WARN | Outbox relay pass failed | The service could not publish waiting events to Pulsar in one pass. They stay in the outbox and are sent on the next pass. | If it repeats, check that Pulsar is reachable and the service's database is healthy; events are not lost. | bil, dsr, df-reg, cns-cmd |
| `CM-USAGE-ALERT-FAILED` | WARN | Usage alert check failed | The usage alert sweep could not check one fiduciary, or the whole pass failed. It is tried again every minute. | If it repeats, check Redis and the billing database. | bil |

## Events

Raised as Keycloak events.

| Code | Title | What it means | What to do | Raised by |
|---|---|---|---|---|
| `cm_otp_rate_limited` | Sign-in code refused by a limit | A Keycloak event raised when a sign-in code was refused because of a limit across sign-ins. The detail cm_otp_limit says which: TOO_MANY_CODES, NETWORK_BUSY or TOO_MANY_WRONG_GUESSES (ADR-061). | Alert on bursts. A burst for one address is someone being targeted; a burst of NETWORK_BUSY from one address is one host spraying. | idn-otp-spi |
