# ADR-088: Calendar months on the dashboard

- **Status:** Accepted
- **Date:** 2026-11-08
- **Issue:** #114

## Context

The dashboard shows the last 7, 30, 90 or 365 days (ADR-049) and can compare them with the stretch before (ADR-078). People who
report monthly ("how did October go, against September?") cannot get that from a rolling window: the last 30 days is not October,
and last month's 30 days are not September's.

## Decision

1. **A month can be asked for.** `GET /api/cns-qry/insights?month=yyyy-MM` (and the CSV export) returns the figures for one calendar
   month **in the time zone asked for**, in place of `days`: its first moment is local midnight on the 1st, and it ends where the next
   month begins there. A month still going ends now. A month in the future, one more than 24 months back, or text that is not
   `yyyy-MM` is a 400.
2. **The comparison is the month before, and fair.** For a month that has ended, it is all of the month before. For the month
   still going, it is only the same stretch from the start of the month before (19 days 10 hours in, against the first 19 days 10 hours
   of September), so "this month so far" is never set against a longer month, and never past the point where the month asked for
   begins. The console words it "same days of last month" or "month before".
3. **The console offers "This month" and "Last month"** beside the four spans, and names the CSV by the month.
4. **Nothing else changes:** the totals, the day-by-day series, the purposes and the later changes are worked out exactly as for a
   span of days, over the month's own interval.

## Not yet / limits

- **Only a month, not a quarter or a year to date;** nor a month chosen from a picker, only the two nearest. The API takes any month
  of the last 24.
- **Comparison is still by totals** (ADR-078), not by purpose or by day.
- **A daily rollup is still not built;** a month is a bounded read, and 24 months back is no more than the 365 days already allowed
  times two.
- **Daylight saving:** a month is the real interval between two local midnights, so in a zone that changes its clocks it can be an hour
  longer or shorter than 24 times its days; the figures are exact for that interval.

## Options considered

- **`from` and `to` dates:** any window, but the comparison then has no obvious partner and every client must work out months and
  zones itself; a month is the case people ask for.
- **Cutting the month in UTC only:** a fiduciary in India would see the first 5 hours 30 minutes of every month in the wrong one.
