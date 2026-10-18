# ADR-036: Batches of consent requests: one template, many people, from JSON, CSV or Excel

- **Status:** Accepted
- **Date:** 2026-10-18

## Context

A fiduciary asks consent of its customers, often all of them at once: a new purpose, a changed notice, a re-consent
campaign. Today the API takes one request per call.

- **Small businesses** keep customers in a spreadsheet and have no systems to call an API.
- **Larger ones** integrate, and need to send thousands without thousands of calls, and to match results to their
  own records.

The monthly allowance is a usage counter the gateway increments once per API call, so a single call creating many
requests must account for each.

## Decision

1. **A batch is one template and a list of people.**
   - The template is one purpose, one set of data categories and one notice; the notice text is required.
   - Each valid person gets an ordinary consent request of their own, with the same notification, saga, read model and
     audit as a single request. Nothing downstream changes.
2. **Three ways in, one engine:**
   - `POST /api/cns-cmd/cns-reqs` for one person (unchanged)
   - `POST /api/cns-cmd/cns-req-batches` with JSON (`recipients`: `contact` or `email` or `phone`, plus an optional
     `ref` of the fiduciary's own)
   - the same endpoint with a multipart upload of a CSV or Excel (`.xlsx`) file, the template as form fields

   The DF Console's "Send to many" uses the upload.
3. **Checked before anything is sent.**
   - Every row is read as an email address or mobile number, normalised, and marked valid, invalid (with why) or a
     duplicate of an earlier row.
   - Columns are found by header name (email, mobile, contact, reference); a header is optional.
   - At most **10,000 rows** and **5 MB** per batch; longer lists are split.
   - `?dryRun=true` stops here and reports the counts, the first problem rows and the allowance left; nothing is
     stored, reserved or sent.
4. **All or nothing on the allowance.**
   - cm-bil-svc reserves the batch's valid count in one atomic add, undone if it overshoots the plan.
   - A batch that doesn't fit is refused whole (429, saying how many fit). Two batches arriving together can't both
     slip under the limit.
   - When a batch finishes, the allowance its failed rows reserved is given back.
5. **Sent in the background, reported row by row.**
   - A stored batch answers 202. A worker sends it in chunks of 200, oldest batch first.
   - Each row's request and its outcome commit in one transaction, so a row is never sent twice.
   - Rows are claimed with `FOR UPDATE SKIP LOCKED`, and a claim older than five minutes may be taken again, so
     instances share the work and a crash strands nothing.
   - `GET .../cns-req-batches/{id}` shows progress, `.../rows` lists outcomes, and `.../report.csv` downloads every row
     with its outcome and consent request id. Report cells starting with `= + - @` are defused so a spreadsheet never
     runs them.
6. **Who may:** a fiduciary's API clients, and its people whose role raises requests (ADR-033). Any of its people may
   read its batches; another fiduciary's batch is simply not found.

## Options considered

- **The DF Console parses files and calls the single-request API in a loop:** nothing new server-side, but slow and
  fragile in a browser, no all-or-nothing allowance, and API integrators get nothing.
- **A synchronous bulk call that creates every request before answering:** simpler to report, but a 10,000-row call
  would hold the connection for minutes and fail halfway on any hiccup.
- **Parsing Excel with Apache POI:** complete, but large; `fastexcel-reader` streams `.xlsx` with a small footprint.
  Legacy `.xls` is not accepted.

## Consequences

- Small businesses send to their whole list from a spreadsheet. Integrators send thousands in one call and match
  results by `ref`.
- One more table pair in cns-cmd and a background worker. The allowance counts each recipient, not each call.
- A person is identified only by contact in a batch. Lists from different batches aren't de-duplicated against each
  other, as people may rightly be asked again for a new purpose.

## Compliance impact

- Every request carries the full notice (DPDPA s.5, GDPR art. 7). The template refuses a batch without notice text.
- Data minimisation:
  - A row keeps only the contact as written, its normalised form, the fiduciary's reference and the outcome.
  - Invalid rows never reach notification.
  - The fiduciary's people see their own lists only.
