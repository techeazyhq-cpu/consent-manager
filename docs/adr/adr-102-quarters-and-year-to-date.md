# ADR-102: Calendar quarters and the year to date on the dashboard

- **Status:** Accepted
- **Date:** 2026-11-09
- **Issue:** #114
- **Builds on:** [ADR-088](adr-088-calendar-months-on-the-dashboard.md)

## Context

ADR-088 let the dashboard show a calendar month, compared fairly with the month before. Reporting does not stop at months: a fiduciary
reports a quarter to its board and a year to its regulator, and "how is this quarter going against the last" is the same question at a
different size. ADR-088 listed "a quarter or a year to date" as not yet.

## Decision

1. **A quarter and a year can be asked for,** in the same way as a month and in place of `days`:
   `GET /api/cns-qry/insights?quarter=yyyy-Qn` (`2026-Q3`) and `?year=yyyy`, and the CSV export. Each is cut at local midnight in the time
   zone asked for. A quarter still going, or the current year, ends now: the year so far is the "year to date".
2. **The comparison follows ADR-088's rule exactly,** one level up: against all of the quarter (or year) before when this one has
   ended, and against only the same stretch of the start of the one before when this one is still going, never past where this one begins.
   The first quarter compares with the fourth quarter of the year before.
3. **The range is bounded:** a quarter from the last 8 up to this one, a year from the last 2 up to this one (the same two years as the
   24 months). A quarter that is not 1 to 4, text that is not `yyyy-Qn` or four digits, a period in the future or too far back, or asking for
   more than one of `month`, `quarter` and `year` at once is a 400.
4. **One path in the code:** a month, a quarter and a year are the same calculation over a first day, the day the next one begins and
   the first day of the one before (`calendarPeriod`); the month's behaviour is unchanged.
5. **The console offers "This quarter" and "Year to date"** beside "This month" and "Last month", words the comparison ("same days of
   last quarter", "quarter before", and the same for the year), titles the figures ("Q3 2026", "2026 so far") and names the CSV file by the period.

## Not yet / limits

- **Only the current quarter and the current year are offered as buttons;** the API takes any quarter of the last 8 and any year of the last
  2, and a picker is still not built.
- **Comparison is by totals and, since [ADR-104](adr-104-purposes-beside-the-window-before.md), by purpose;** not by day.
- **A daily rollup is still not built:** a year is a bounded read (366 days, as the 365 days already allowed), but a very large
  fiduciary's year takes as long as its 12-month window does today.
- **Daylight saving and leap years:** a period is the real interval between two local midnights, so its length varies by an hour in zones that
  change clocks and a leap year's is a day longer; the figures are exact for that interval, and the same-stretch comparison is by elapsed
  time, not by calendar day.
- **A fiscal year** (starting in April, say) is not offered.
