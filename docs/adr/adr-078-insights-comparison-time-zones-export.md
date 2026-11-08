# ADR-078: The dashboard compares with the period before, cuts days in a time zone, and exports

- **Status:** Accepted
- **Date:** 2026-11-08
- **Issue:** #114

## Context

The DF Console dashboard (ADR-049) shows what became of the consent requests a fiduciary raised over 7 days to 12 months. ADR-049
left four things for later: a comparison with the previous period, time zones (days were UTC), exporting the figures, and a daily
rollup for very large fiduciaries. A figure on its own does not say whether it is good: 40 refusals is a different thing
after 10 than after 100. A fiduciary in Mumbai reads "20 October" as a day that ends at 18:30 UTC the day before. And a compliance
or product team wants the figures in a spreadsheet, not on a screen.

## Decision

1. **Comparison is asked for, not assumed.** `GET /api/cns-qry/insights?days=N&compare=true` adds `previous`: the totals (outcomes,
   time to decide, later changes) of the window of equal length that ends where this one begins. The detail (days, purposes) is
   not repeated for it. Without `compare` nothing extra is queried. The console shows "up 20% on the 30 days before" under
   the raised, given, refused and withdrawn figures, and says "none in the 30 days before" rather than a percentage of zero.
2. **Days are cut in the zone asked for.** `tz` is an IANA name; the dashboard offers the viewer's own zone and UTC. The window is
   still `[now - N days, now)` as an exact interval, so totals do not change with the zone; only which date a day belongs to,
   and therefore which rows the day-by-day series has. An unknown zone, or a window that is not a number, is a 400. The answer says
   which zone it used.
3. **Export is the same figures as CSV.** `GET /api/cns-qry/insights/export?days&tz&view=days|purposes` returns `text/csv` as an
   attachment: the day-by-day series, or the purposes with their outcomes. Counts, dates and purpose codes only, as on screen:
   no request or principal is identified, so the file carries nothing the dashboard does not. A cell that starts like a spreadsheet
   formula is prefixed with an apostrophe, though today none can.
4. **Same access as the dashboard:** the fiduciary is the token's, never the request's, and anyone but a fiduciary's people and
   systems gets 403.

## Not yet / limits

- **A daily rollup is not built.** Each request still reads the read model directly. Fine for the volumes seen; a fiduciary raising
  millions of requests a month needs a table of daily counts, and the rollup would be the place to also keep zone-specific days.
- **Comparison covers totals only,** not purposes or days, and a comparison of two windows of different length is not offered.
- **The window is the last N days from now,** not calendar weeks or months, so "this month against last month" is not possible yet.
- **Only two views are exported;** the time-to-decide and later-changes figures are on screen and in the JSON.
- **The console offers two zones** (the viewer's and UTC). The API takes any.

## Options considered

- **Always return the previous window:** doubles the queries of every dashboard load for a figure most loads do not show.
- **Cutting the window itself at the zone's midnights:** makes the totals depend on the zone and the window a day shorter or longer.
- **Exporting from the browser's copy of the JSON:** the file would not be reproducible by a script; an endpoint is.
