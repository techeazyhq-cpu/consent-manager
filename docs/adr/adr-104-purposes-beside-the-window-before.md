# ADR-104: Purposes beside the window before on the dashboard

- **Status:** Accepted
- **Date:** 2026-11-09
- **Issue:** #114
- **Builds on:** [ADR-078](adr-078-insights-comparison-time-zones-export.md), [ADR-088](adr-088-calendar-months-on-the-dashboard.md), [ADR-102](adr-102-quarters-and-year-to-date.md)

## Context

The dashboard can set any window against the one before it, but only by totals: "raised, up 20%". A fiduciary asks the next question at
once: up where? A total that rises because one purpose doubled, while another stopped being asked, reads the same as one that rose evenly.
The earlier records listed "by purpose" as not yet.

## Decision

1. **The previous window carries its purposes.** `previous.purposes` in the insights answer has the outcomes of each purpose in the
   window before, most raised first, built from the same query as the current window's purposes (the totals of `previous` are those
   purposes added up). A purpose with no requests in that window is simply absent. No new query: the figures were already read and
   thrown away.
2. **The console's "By purpose" table shows how each figure moved** beside *Raised* and *Given* when comparing: `+20%`, `-30%`,
   `same`, or `new` for a purpose with none before. A purpose that had requests before and none now is listed last, with zeros, so
   that one that went quiet is seen and not just missing. A line above the table says what the figures are against ("the 30 days
   before", "same days of last quarter").
3. **It works for every window** (days, month, quarter, year), because it hangs on the same comparison; and does nothing when not
   comparing: the answer, the table and the CSV are as they were.
4. **The CSV export does not change.** It is never a comparison (ADR-078).

## Not yet / limits

- ~~**Only *Raised* and *Given* are compared per purpose.**~~ Refused, awaiting and the rate are compared too ([ADR-106](adr-106-every-purpose-figure-beside-the-window-before.md));
  the time to decide is still not.
- **Not by day:** the day-by-day series is still only for the current window.
- **A percentage on a small number misleads** ("+300%" from 1 to 4). The figure shown is the plain percentage; a purpose with few
  requests is best read from the counts beside it.
- **Purposes are matched by code,** so a purpose that is renamed shows as one that went quiet and one that is new.
- **A daily rollup is still not built,** so a large fiduciary's wide window takes as long as before.
