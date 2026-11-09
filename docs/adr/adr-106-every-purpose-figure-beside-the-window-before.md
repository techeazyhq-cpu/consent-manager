# ADR-106: Every figure of a purpose beside the window before

- **Status:** Accepted
- **Date:** 2026-11-09
- **Issue:** #114
- **Builds on:** [ADR-104](adr-104-purposes-beside-the-window-before.md)

## Context

ADR-104 put the window before's purposes in the answer and showed how *Raised* and *Given* moved in the console's "By purpose" table. It
left the rest, *Refused*, *Awaiting* and the share who gave consent, for later. The data was already in the answer: the previous
window carries every outcome of every purpose. What a fiduciary reads in that table is mostly the refusals and the rate, so those are what
should be compared.

## Decision

1. **Refused and Awaiting** show the same short change as Raised and Given: `+20%`, `-30%`, `same`, `new` (none before, some now).
2. **The share who gave consent ("Given of decided")** shows its move in **percentage points**, not as a percentage of a percentage:
   from 50% to 90% is `+40 pts`, not `+80%`. It says nothing when nobody had decided in one of the two windows, because there is no rate
   to compare.
3. **A line above the table says so** ("Beside each figure, how it moved against the 30 days before ... The rate moves in percentage
   points").
4. **No change to the API or the CSV:** this is the console showing what the answer already held.

## Checked

- Unit tests of the points helper (up, down, same, none when either rate is missing, from a purpose's outcomes) and of the short change.
- **A render test of the real table** (`Insights.render.test.tsx`), with an answer for a window and the one before: a purpose in both
  (raised, given, refused, awaiting and rate all moved as expected), a purpose that is new, and one that went quiet, listed last with
  its figures falling to nothing. 81 tests pass in the package; the build passes; no new type errors.

## Not yet / limits

- **Not looked at in a browser:** the table was rendered by a test, not seen in the console with a real sign-in.
- **Not the median time to decide, or the notified-or-not count:** they are in the answer and not compared in the table.
- **Not by day,** and a percentage on a small number still misleads (ADR-104).
- **Rounding:** the rate is in whole percent, so a move of under a point can show as `same`.
