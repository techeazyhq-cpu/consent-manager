# ADR-049: Dashboard v1 (outcomes and time to decide) and developer docs in the DF Console

- **Status:** Accepted
- **Date:** 2026-10-20
- **Implements:** [ADR-032](adr-032-channels-per-audience-web-mobile-console-developer-portal.md) for the DF Console
  dashboard and developer portal, in part

## Context

The dashboard counted the requests in each status from every row the console had loaded. It couldn't say how many
people refused rather than ignored a request, how long they took, or what happened after consent was given. It also
grew with the number of requests. Developers had the OpenAPI file in the repository and the playground, but no page
that told them, with their own details, how to connect.

## Decision

1. **`GET /api/cns-qry/insights?days=N`** (1 to 365, default 30) gives the caller's own fiduciary's figures. The
   fiduciary comes from the token; a principal gets 403. Every member and API client can read it. It returns:
   - **Outcomes** of the requests raised in the window, by each request's first decision: raised, given, refused,
     awaiting a decision, and couldn't be notified. A consent given then withdrawn or lapsed still counts as given.
     The rate is of those who decided.
   - **Time to decide**, from request to first decision: median, 90th percentile, and the share decided within a day.
   - **Each purpose's** outcomes and median.
   - **Every day** of the window (UTC): raised that day, and given or refused that day.
   - **Changes** made in the window to any consent, whenever given: withdrawn, narrowed or shortened, renewed, lapsed.
   Counts and durations only; no request or principal is identified.
2. **From the read model, as it is.** A decision is a request's first move to ISSUED or DENIED in `cns_hst_rd_mdl`.
   Changes after consent are ISSUED to ISSUED with a change code, so they never count as a decision again. Four
   aggregate queries run on existing indexes. Over a year of 7,826 requests the heaviest takes about 90 ms.
3. **The dashboard** leads with these figures, with a 7-day, 30-day, 90-day or 12-month period. Its parts:
   - outcome tiles, each opening the matching requests
   - time-to-decide tiles
   - a chart of given and refused by day, with a tooltip on each day and a table view
   - a per-purpose table
   - the purpose tiles, as before

   The chart's two colours are categorical slots 1 and 2, checked against the console's dark surface for contrast and
   colour-blind separation.
4. **Developer docs** is a DF Console page under Developers, open to every member. It has:
   - the organisation's API base URL, token URL and client id
   - a four-step quick start filled in with them: token, ask, follow, check before use
   - checking webhook signatures (ADR-047)
   - the OpenAPI, AsyncAPI and Postman files to download

   The files ship with the console build, so they match the API it calls. Each is loaded only when downloaded.

## Not yet

- **A daily rollup** for fiduciaries with millions of requests, when the aggregate queries stop being fast enough.
- **Time zones:** days are UTC.
- **Comparison with the previous period**, and exports of the figures.
- **A public developer portal** outside the console (ADR-032).

## Consequences

- The dashboard no longer depends on loading every request. The requests page still does, until it pages.
- "Given" means consent was given, whatever happened after. What is in force now is on the purpose tiles and the
  requests page.
