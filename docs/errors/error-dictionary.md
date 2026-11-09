<!-- Generated from backend/libs/cm-web-kit/src/main/resources/cm-error-dictionary.json by
     node scripts/error-dictionary.mjs --write. Do not edit by hand: edit the dictionary. -->

# Error dictionary

Every error, warning and event this platform raises, in one place ([ADR-062](../adr/adr-062-error-dictionary.md)).
173 entries: 157 API problems, 15 operational log codes and 1 event.

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
| `ai-draft-personal-data` | 422 | The description seems to hold personal data | The text to draft from looks like it holds an email address, a phone, a PAN, an Aadhaar-shaped or a card number. The drafting model is not where anyone's data goes, so the request was refused and nothing was sent to it. | Describe the purpose in general words, with no one's details, and ask again. | no | cns-cmd |
| `ambiguous-credentials` | 400 | Two kinds of credentials sent | The request carried both an API key and a bearer token, so it is unclear which identity is calling. | Send one credential: either the API key headers or a bearer token. | no | gw |
| `confirmation-required` | 400 | Confirmation needed | Closing an account needs the confirmation words typed exactly. | Type the words the page asks for. | no | dsr |
| `invalid-answer` | 400 | Answer not valid | An answer needs a reply, and a refusal also needs its legal ground. The detail says which. | Add what is missing. | no | dsr |
| `invalid-api-key` | 400 | API key request not valid | The request to issue an API key was refused. The detail says why. | Correct the request as the detail says. | no | df-reg |
| `invalid-batch` | 400 | Batch not valid | The batch request was refused. The detail says why. | Correct the batch as the detail says. | no | cns-cmd |
| `invalid-change` | 400 | Change not valid | The change to the consent or purpose was refused. The detail says why. | Correct the change as the detail says. | no | cns-cmd |
| `invalid-count` | 400 | Count out of range | The number of requests to reserve or release must be from 1 to 100,000. | Send a count in range. | no | bil |
| `invalid-credit-reason` | 400 | A credit note needs a reason | The reason is missing or longer than 500 characters. | Give a reason of up to 500 characters. | no | bil |
| `invalid-decision` | 400 | Decision not valid | A decline needs a reason, or the note was too long. | Say why the request is declined, in the note. | no | bil |
| `invalid-df-id` | 400 | Fiduciary id not valid | A fiduciary id must be a UUID. | Use the id as given by the platform. | no | df-reg |
| `invalid-df-profile` | 400 | Fiduciary profile not valid | A field of the fiduciary's profile was refused. The detail names it. | Correct the profile field named in the detail. | no | df-reg |
| `invalid-dp-request` | 400 | Request not valid | The request is missing its consent or details, or they are too long. The detail says which. | Correct the request as the detail says. | no | dsr |
| `invalid-grant` | 400 | Choice not valid | The data or validity the person chose is not allowed for this request. The detail says why. | Choose within what the request offers. | no | cns-cmd |
| `invalid-kind` | 400 | Kind of request not recognised | The kind of request must be RELAY or GRIEVANCE. | Use one of them. | no | dsr |
| `invalid-list` | 400 | Recipient list not valid | The recipient list could not be read. The detail says why. | Correct the list as the detail says. | no | cns-cmd |
| `invalid-logo` | 400 | Logo not valid | The logo is not a supported image. | Upload a PNG, JPEG or SVG logo. | no | df-reg |
| `invalid-member` | 400 | Team member not valid | A required field of the new member is missing or not valid. The detail names it. | Correct the field named in the detail. | no | df-reg |
| `invalid-month` | 400 | The month is not valid | The month is not in the form yyyy-MM, for example 2026-10. | Send the month as yyyy-MM. | no | bil |
| `invalid-operator` | 400 | Operator not valid | A required field of the new operator is missing or not valid. | Correct the field named in the detail. | no | stf |
| `invalid-parameter` | 400 | Parameter not valid | A query parameter has the wrong form. The detail names it. | Correct the parameter. | no | cns-cmd |
| `invalid-period` | 400 | Month not valid | A month is written like 2026-11. | Use that form. | no | bil |
| `invalid-plan-change-request` | 400 | Plan change request not valid | The request names no plan, the plan the fiduciary already has, or a note that is too long. | Choose a different plan and keep the note short. | no | bil |
| `invalid-price` | 400 | The price is not valid | A monthly price is a whole number of minor units from 0 up to a thousand million. | Send monthlyMinor as a non-negative whole number. | no | bil |
| `invalid-processor` | 400 | Processor is not valid | The name, role, country or contact of the processor is missing or malformed. | Give a name of up to 200 characters, a role of up to 500, a two-letter ISO country code and an email address for the contact. | no | cns-cmd |
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
| `otp-send-invalid` | 400 | A sign-in code request is missing something | The request to send a sign-in code had no body, or no channel, destination or code. It comes from Keycloak's sign-in step, which always sends all three, so a request like this is something else calling the endpoint or a mistake in a change. | Send a channel, a destination and the code. If Keycloak sent it, look at the sign-in step's version against the notification service's. | no | ntf |
| `payload-too-large` | 413 | Request body too large | The request body is larger than the gateway accepts. | Send a smaller body, or split it. | no | gw |
| `push-token-invalid` | 400 | Push token not valid | The device token is missing or not valid. | Register the device again. | no | ntf |
| `signup-invalid-token` | 400 | Confirmation link not valid | The link to confirm an application's contact address is not valid, has expired or was already used. | Apply again to get a new link. | no | df-reg |
| `terms-document-unknown` | 400 | Unknown document | The document named to accept is not one of the consent manager documents: terms or dpa. | Name terms or dpa. | no | df-reg |
| `unknown-sub-pln` | 400 | Unknown plan | There is no plan with that name. | List the plans and use one of their names. | no | bil, df-reg |
| `unsupported-list` | 415 | List type not supported | The file is not JSON, CSV or Excel. | Send the list as JSON, CSV or .xlsx. | no | cns-cmd |

### Authentication

| Code | Status | Title | What it means | What to do | Retry | Raised by |
|---|---|---|---|---|---|---|
| `api-key-invalid` | 401 | API key not accepted | The API key is missing, unknown, malformed or revoked, or its signature could not be checked. The reasons are not told apart on purpose. | Check the key, and that it was copied whole. Issue a new key in the DF Console if it was revoked. | no | df-reg, gw |
| `internal-key-invalid` | 401 | The service key is missing or wrong | A request to send a sign-in code did not carry the key cm-ntf-svc asks for, or carried a different one. | Set the same value as cm-ntf-svc's cm.ntf.internal-api-key in Keycloak's ntf-api-key server setting. | no | ntf |
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
| `leave-proposal-same-person` | 403 | A second person must decide | The member of staff who proposed this cannot approve or reject it: ending a contract takes two people. | Ask another administrator to decide it. | no | df-reg |
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
| `ai-drafting-disabled` | 404 | Drafting with a model is not switched on | The platform has no drafting model set up (cm.cnscmd.ai-drafting.enabled is off), so a purpose cannot be drafted from a description. | Write the purpose by hand, or ask whoever runs the platform to set up a model (ADR-111). | no | cns-cmd |
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
| `invoice-not-found` | 404 | Invoice not found | There is no such invoice for the caller. | List the invoices and use one of their ids. | no | bil |
| `leave-proposal-not-found` | 404 | Proposal not found | There is no such proposal. | Check the proposal id. | no | df-reg |
| `member-not-found` | 404 | Team member not found | No member with that id belongs to this fiduciary. | List the team and use an id from it. | no | df-reg |
| `plan-change-request-not-found` | 404 | Plan change request not found | No plan change request with that id is visible to the caller. | List the requests and use an id from the list. | no | bil |
| `processor-not-found` | 404 | Processor not found | There is no such processor in the fiduciary's register. | List the register and use one of its ids. | no | cns-cmd |
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
| `df-already-registered` | 409 | Organisation already registered | An organisation is already registered with this contact address. | Sign in to the DF Console, or contact the platform. | no | df-reg |
| `dp-request-open` | 409 | Already asked | The person already has this request open for this consent. | Wait for the answer, or withdraw the open one. | no | dsr |
| `erasure-running` | 409 | Account is already being closed | An erasure for this account is already under way. | Wait for it to finish. | no | dsr |
| `last-owner` | 409 | A fiduciary must keep an Owner | The change would leave the fiduciary without an Owner, which is not allowed. | Make someone else an Owner first. | no | df-reg |
| `leave-proposal-pending` | 409 | A proposal already waits | A proposal to end or keep this fiduciary's contract already waits for a second member of staff. | Approve, reject or withdraw the waiting proposal first. | no | df-reg |
| `owner-exists` | 409 | The fiduciary already has an Owner | A fiduciary has one Owner, who manages its team. | Ask the Owner to add people. | no | df-reg |
| `plan-change-request-pending` | 409 | A plan change is already waiting | The fiduciary already has a plan change request waiting for a decision. | Withdraw the waiting request to ask for another plan. | no | bil |
| `processor-exists` | 409 | Processor already registered | A processor with that name is already in the fiduciary's register (names are compared ignoring case). | Use the existing entry, or choose a different name. | no | cns-cmd |
| `purpose-exists` | 409 | Purpose already exists | A purpose with that code exists already. | Draft a new version of it instead of creating it again. | no | cns-cmd |
| `purpose-version-superseded` | 409 | Purpose version replaced | A newer version of the purpose has been published since, so a request cannot name this one. | Ask for the current version. | no | cns-cmd |
| `request-replayed` | 409 | Request already used | The nonce of this signed request was used before, so it was refused as a replay. | Sign every request, retries included, with a new nonce. | no | df-reg |
| `terms-version-outdated` | 409 | Document version not current | The version offered for acceptance is not the version in force, so the acceptance was refused. The detail names the current version. | Read the current version (GET /api/df-reg/me/terms), show it to the Owner and accept that one. | no | df-reg |

### State

| Code | Status | Title | What it means | What to do | Retry | Raised by |
|---|---|---|---|---|---|---|
| `api-key-revoked` | 409 | API key already revoked | The key was revoked earlier, and a revoked key cannot be changed or used. | Issue a new key if one is needed. | no | df-reg |
| `billing-period-open` | 409 | The month has not ended | Invoices are issued for calendar months (UTC) that have ended. | Run the invoices after the month ends. | no | bil |
| `cns-art-expired` | 409 | Consent has lapsed | The consent's validity ended. | Ask for consent again. | no | cns-cmd |
| `cns-art-not-active` | 409 | Consent is not active | The consent has been withdrawn or has lapsed, so its token cannot be refreshed. | Ask for consent again. | no | cns-cmd |
| `cns-art-not-in-force` | 409 | Consent is not in force | The consent is not in force, so it cannot be changed. | Read the consent's state first. | no | cns-cmd |
| `cns-art-not-issued` | 409 | Consent is not in force | The consent was never given, or has been withdrawn, so this cannot be done. | Read the consent's state first. | no | cns-cmd |
| `cns-req-dp-not-linked` | 409 | Request not yet linked to a person | The request was sent to an address the person has not signed in with yet, so it cannot be decided. | The person must sign in with the address it was sent to. | no | cns-cmd |
| `cns-req-not-pending` | 409 | Consent request already decided | The request was already given, refused or withdrawn. | Read its current state; raise a new request if needed. | no | cns-cmd |
| `delivery-pending` | 409 | Delivery still being tried | The delivery has not finished, so it cannot be replayed yet. | Wait until it is delivered or failed. | no | cns-cmd |
| `df-activation-required` | 409 | Activate the fiduciary first | Its team can only be managed once the fiduciary has been activated. | Activate the fiduciary, then try again. | no | df-reg |
| `df-cannot-activate` | 409 | Fiduciary cannot be activated | Only an application waiting for activation can be activated: this one is already active, or was suspended, closed or rejected. | Check the fiduciary's status. | no | df-reg |
| `df-cannot-leave` | 409 | Fiduciary cannot leave now | Only an active or suspended fiduciary can ask to leave; this one is pending, already leaving or closed. | Check the fiduciary's status. | no | df-reg |
| `df-cannot-reject` | 409 | Application cannot be rejected | Only an application still waiting for activation can be rejected. | Check the fiduciary's status. | no | df-reg |
| `df-cannot-suspend` | 409 | Fiduciary cannot be suspended | Only an active fiduciary can be suspended; this one is pending or already suspended. | Check the fiduciary's status. | no | df-reg |
| `df-leaving` | 403 | Fiduciary is leaving | The fiduciary this API key belongs to has asked to leave the platform. Nothing new is accepted during the wind-down; its records stay readable until it closes. | An Owner can cancel the departure once before the wind-down ends, in the DF Console. | no | df-reg |
| `df-no-legal-hold` | 409 | No legal hold to release | The fiduciary is not under a legal hold. | Check the fiduciary's status. | no | df-reg |
| `df-not-active` | 403 | Fiduciary not active | The fiduciary is registered but not active, or has been suspended, so it cannot act yet. | Activate the fiduciary, or contact the platform if it was suspended. | no | cns-cmd, df-reg |
| `df-not-leaving` | 409 | Fiduciary is not leaving | There is no departure to cancel. | Check the fiduciary's status. | no | df-reg |
| `df-not-suspended` | 409 | Fiduciary is not suspended | Only a suspended fiduciary can be resumed. | Check the fiduciary's status. | no | df-reg |
| `df-suspended` | 403 | Fiduciary suspended | The fiduciary this API key belongs to has been suspended by the consent manager. Nothing new is accepted until it is resumed; its records are untouched. | Contact the consent manager's support. The suspension reason was given to your Owner. | no | df-reg |
| `dp-request-closed` | 409 | Request already closed | The request was already answered, refused or withdrawn. | Read its current state. | no | dsr |
| `email-not-available` | 409 | No email address to use | Email notifications go to the address the person signed in with. They signed in with a mobile number, so there is none. | Leave email off, or sign in with an email address. | no | ntf |
| `erasure-not-stuck` | 409 | Erasure is not stuck | Only a stuck erasure can be retried. | Read its state first. | no | dsr |
| `export-unavailable` | 409 | Export not available now | The organisation's data can be exported while it is active, suspended or leaving. Once it has closed its team and keys are gone and the records are kept only for their retention. | Take the export during the wind-down. | no | df-reg |
| `invoice-already-credited` | 409 | The invoice cannot be credited | The invoice has been credited already, or is a credit note. | Read the credit note that exists; an invoice is credited once. | no | bil |
| `invoice-not-payable` | 409 | The invoice cannot be marked paid | Only an invoice that is still issued is marked paid. | Read the invoice: it may be paid or credited already. | no | bil |
| `leave-cancel-used` | 409 | Departure already cancelled once | A departure can be called off once, and this fiduciary has already used that. | The wind-down continues. Contact the consent manager's support if this is a mistake. | no | df-reg |
| `leave-proposal-decided` | 409 | Proposal already decided | The proposal was already approved, rejected or withdrawn. | Nothing more to do; propose again if it is still wanted. | no | df-reg |
| `no-draft` | 409 | No draft to publish | The purpose has no draft, so there is nothing to publish. | Draft a change first. | no | cns-cmd |
| `plan-change-request-decided` | 409 | Plan change already decided | The request was already approved, declined or withdrawn. | Read its current state; nothing more to do. | no | bil |
| `processor-retired` | 409 | Processor is retired | A purpose draft or publication names a processor that has been retired from the register. | Remove it from the recipients and transfers, or reinstate the processor. | no | cns-cmd |
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
| `ai-drafting-rate-limited` | 429 | Too many drafts asked for | This fiduciary has asked for as many model drafts in the last hour as it may. | Wait and ask again later, or write the purpose by hand. | yes | cns-cmd |
| `otp-send-limited` | 429 | Too many sign-in codes sent | The notification service has sent as many sign-in codes to this address or number, or by this channel, as it allows in the hour or day, whoever asked. The sign-in is told to wait. | Wait and ask for a code again later; do not retry in a loop. If it is a real person, they can use the code already sent. | yes | ntf |
| `rate-limit-exceeded` | 429 | Too many requests a second | The fiduciary's plan allows only so many requests a second, and this call went over it. | Wait for the Retry-After interval and slow down, or ask for a plan with a higher rate. | yes | gw |
| `signup-rate-limited` | 429 | Too many sign-up requests | Too many applications were made from this address or for this contact address, or sign-up is busy today. | Try again later; a person is held to three applications a day for one contact address. | yes | df-reg, gw |

### Dependency

| Code | Status | Title | What it means | What to do | Retry | Raised by |
|---|---|---|---|---|---|---|
| `ai-draft-invalid` | 502 | The model's draft was not usable | The model answered twice, and neither answer passed the rules a purpose must meet (not JSON, no data listed, no notice, a field out of bounds). Nothing was kept. | Describe the purpose a little differently, or write it by hand. | no | cns-cmd |
| `ai-drafting-busy` | 503 | The drafting model is busy | The model is already working on as many drafts as this instance allows at once. | Try again in a moment. | yes | cns-cmd |
| `ai-drafting-unavailable` | 503 | The drafting model could not be reached | The model endpoint did not answer in time or refused the connection. | Try again shortly. If it keeps failing, whoever runs the platform checks the model server (runbook: AI drafting). | yes | cns-cmd |
| `api-key-check-unavailable` | 503 | API key could not be checked | The service that checks API keys is not answering. This says nothing about whether the key is valid. | Retry after the Retry-After interval. If it keeps failing, report the correlation id. | yes | gw |
| `billing-unavailable` | 503 | Billing could not be reached | A fiduciary's departure could not be called off because Billing, which must bill it again, did not answer in time. Nothing was changed: the fiduciary is still leaving, and the one cancellation is not used up. | Try again in a moment. If it keeps failing, platform staff check cm-bil-svc. | yes | df-reg |
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
| `CM-DF-CLOSING-FAILED` | WARN | Closing a fiduciary failed | A fiduciary whose wind-down has ended could not be closed: a step failed, such as ending its consents or removing its team. It stays LEAVING and is tried again on the next pass; every step can be repeated. | Read the log line for the failing step, fix the cause (often cm-cns-cmd-svc or Keycloak unreachable) and it closes on the next pass. | df-reg |
| `CM-DF-PURGE-FAILED` | WARN | Retention purge of a fiduciary failed | Deleting a closed fiduciary's records past their retention failed: one of cm-cns-cmd-svc, cm-cns-qry-svc or cm-aud-svc could not be reached or refused. Nothing partly deleted is lost; every call can be repeated. | Read the log line for the failing service, fix the cause and the next hourly pass retries. | df-reg |
| `CM-ERASURE-STEP-FAILED` | WARN | Account erasure step failed | One attempt at an erasure step failed. It is tried again every two minutes until it gives up. | No action unless CM-ERASURE-STUCK follows; check the named step's service if it repeats. | dsr |
| `CM-ERASURE-STUCK` | ERROR | Account erasure stuck | An account erasure gave up on a step after eight attempts and stopped. Part of the person's data is erased and part is not. | Find the cause for the step named in the message and retry it in CM Admin; see docs/runbooks/erasure.md. | dsr |
| `CM-ERRDICT-UNLISTED` | ERROR | Problem missing from the error dictionary | A service answered a problem whose code is not in the error dictionary, so it got a title made from its name. CI should have refused it. | Add the code to cm-error-dictionary.json and run node scripts/error-dictionary.mjs --write. | aud, bil, cns-cmd, cns-qry, df-reg, dsr, gw, ntf, stf |
| `CM-INTERNAL-ERROR` | ERROR | Unexpected error ended a request | An exception nobody planned for ended a request. The caller was answered with internal-error and the correlation id; the exception and its stack trace are in this log message. | Find the log line by the correlation id the caller quotes, and fix the cause. A burst of them is an incident. | aud, bil, cns-cmd, cns-qry, df-reg, dsr, gw, ntf, stf |
| `CM-INVOICE-REMINDER-FAILED` | WARN | An invoice reminder could not be raised | The invoice reminder sweep could not raise the reminder for one overdue invoice, or the whole pass failed. It is tried again every hour, and a reminder step is recorded only once it has been raised. | If it repeats, check the billing database and the outbox relay. A fiduciary is not told about an overdue invoice until it clears. | bil |
| `CM-INVOICE-RUN-FAILED` | WARN | Invoice run failed | The monthly invoice run for last month failed part way. Invoices already issued stay; it is tried again every hour. | If it repeats, check the billing database and the plan prices, then run the month by hand from the admin console. | bil |
| `CM-LEAVING-BILLING-FAILED` | WARN | Billing could not be told that a fiduciary is leaving | A fiduciary began to leave, and cm-df-reg-svc could not reach cm-bil-svc to end its billing for the wind-down. The departure went ahead; billing is ended again when the fiduciary closes (ADR-084), so the cost is that it stays billed until then. | Check cm-bil-svc. To stop billing now, call PUT /api/bil/dfs/{id}/billing-end as an administrator (ADR-109). | df-reg |
| `CM-NTF-INTERNAL-OPEN` | WARN | Sign-in codes can be requested by anything on the network | cm-ntf-svc has no internal API key, so any caller that can reach it can ask it to send sign-in codes. Logged at start-up. | Set cm.ntf.internal-api-key (NTF_INTERNAL_API_KEY in the chart's secret) and the same value in Keycloak. | ntf |
| `CM-NTF-INTERNAL-ROTATING` | WARN | The previous sign-in service key is still accepted | cm-ntf-svc has cm.ntf.internal-api-key-previous set, so requests to send a sign-in code are accepted with either the current key or the old one. Logged at start-up; meant only for the time it takes to change the key (ADR-101). | Once cm.ntf.internal.key.previous.used has stopped rising, remove NTF_INTERNAL_API_KEY_PREVIOUS from the secret and restart the service. | ntf |
| `CM-OTP-LIMITS-EXEMPT` | WARN | Sign-in code limit lifted for some addresses | Keycloak started with addresses exempt from the limit on sign-in codes sent to one address (ADR-061). That is for the local stack, where a test kit signs in as the same accounts many times an hour. | In production this is a misconfiguration: remove spi-authenticator-cm-otp-authenticator-exempt-addresses from Keycloak's settings and restart it. | idn-otp-spi |
| `CM-OTP-NTF-KEY-MISSING` | WARN | Keycloak sends no key to cm-ntf-svc | The sign-in step has no ntf-api-key server setting, so it asks cm-ntf-svc to send codes without a key. That works only while cm-ntf-svc does not ask for one. Logged at start-up. | Set the ntf-api-key server setting (KC_SPI_AUTHENTICATOR_CM_OTP_AUTHENTICATOR_NTF_API_KEY) to the value of cm-ntf-svc's internal API key. | idn-otp-spi |
| `CM-OUTBOX-RELAY-FAILED` | WARN | Outbox relay pass failed | The service could not publish waiting events to Pulsar in one pass. They stay in the outbox and are sent on the next pass. | If it repeats, check that Pulsar is reachable and the service's database is healthy; events are not lost. | bil, dsr, df-reg, cns-cmd |
| `CM-USAGE-ALERT-FAILED` | WARN | Usage alert check failed | The usage alert sweep could not check one fiduciary, or the whole pass failed. It is tried again every minute. | If it repeats, check Redis and the billing database. | bil |

## Events

Raised as Keycloak events.

| Code | Title | What it means | What to do | Raised by |
|---|---|---|---|---|
| `cm_otp_rate_limited` | Sign-in code refused by a limit | A Keycloak event raised when a sign-in code was refused because of a limit across sign-ins. The detail cm_otp_limit says which: TOO_MANY_CODES, NETWORK_BUSY or TOO_MANY_WRONG_GUESSES (ADR-061). | Alert on bursts. A burst for one address is someone being targeted; a burst of NETWORK_BUSY from one address is one host spraying. | idn-otp-spi |
