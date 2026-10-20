/*
 * Copyright 2026 Vasantha Kumar
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * @author Vasantha Kumar <vasantha.kumar@hotmail.com>
 */

import { useEffect, useState } from "react";
import { insights, type CnsLcySts, type DfInsightsDto } from "api-client";
import { ErrorBanner, Spinner, friendlyError, humanise } from "ui-kit/src/index";
import type { Console } from "./console";
import { PERIODS, decidedRate, fmtDay, fmtDuration, niceMax, share } from "./dashboard";
import { purposeHref } from "./routes";
import { freshToken } from "./token";

/** Validated for the console's dark surface (dataviz validator, categorical slots 1 and 2). */
const GIVEN = "#3987e5";
const REFUSED = "#d95926";

function Stat({ value, label, note, onClick }: { value: string; label: string; note?: string; onClick?: () => void }) {
  const body = (
    <>
      <b>{value}</b>
      <span>{label}</span>
      {note ? <span className="df-stat-note">{note}</span> : null}
    </>
  );
  return onClick ? (
    <button className="cm-stat df-stat-button" onClick={onClick}>
      {body}
    </button>
  ) : (
    <div className="cm-stat">{body}</div>
  );
}

/** Given and refused, day by day, stacked; hover a day for its figures, or read them as a table. */
function DecisionsChart({ days }: { days: DfInsightsDto["days"] }) {
  const [hover, setHover] = useState<number | null>(null);
  const [table, setTable] = useState(false);
  const W = 720;
  const H = 180;
  const pad = { top: 8, right: 8, bottom: 22, left: 32 };
  const max = niceMax(Math.max(...days.map((d) => d.given + d.refused), 0));
  const slot = (W - pad.left - pad.right) / days.length;
  const bar = Math.max(2, Math.min(28, slot * 0.7));
  const y = (v: number) => pad.top + (H - pad.top - pad.bottom) * (1 - v / max);
  const every = Math.ceil(days.length / 8);
  const h = hover != null ? days[hover] : null;

  return (
    <div className="df-chart">
      <div className="df-chart-head">
        <h3 className="cm-section-title" style={{ margin: 0 }}>
          Decisions by day
        </h3>
        <span className="df-legend">
          <i style={{ background: GIVEN }} /> Given
          <i style={{ background: REFUSED }} /> Refused
        </span>
        <button className="df-link-button" onClick={() => setTable(!table)}>
          {table ? "Show as chart" : "Show as table"}
        </button>
      </div>
      {table ? (
        <div style={{ overflowX: "auto", maxHeight: 260 }}>
          <table className="cm-table">
            <thead>
              <tr>
                <th>Day (UTC)</th>
                <th>Raised</th>
                <th>Given</th>
                <th>Refused</th>
              </tr>
            </thead>
            <tbody>
              {days.map((d) => (
                <tr key={d.date}>
                  <td>{fmtDay(d.date)}</td>
                  <td>{d.raised}</td>
                  <td>{d.given}</td>
                  <td>{d.refused}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <div style={{ position: "relative" }}>
          <svg
            viewBox={`0 0 ${W} ${H}`}
            width="100%"
            role="img"
            aria-label="Consents given and refused each day"
            onMouseLeave={() => setHover(null)}
          >
            {[0, max / 2, max].map((v) => (
              <g key={v}>
                <line x1={pad.left} x2={W - pad.right} y1={y(v)} y2={y(v)} stroke="var(--cm-border)" strokeWidth={1} />
                <text x={pad.left - 6} y={y(v) + 4} textAnchor="end" fontSize={11} fill="var(--cm-text-dim)">
                  {Number.isInteger(v) ? v : ""}
                </text>
              </g>
            ))}
            {days.map((d, i) => {
              const x = pad.left + slot * i + (slot - bar) / 2;
              const gTop = y(d.given);
              const rTop = y(d.given + d.refused);
              const base = y(0);
              // 2px of surface between the two segments, rounded ends away from the baseline
              const gap = d.given > 0 && d.refused > 0 ? 2 : 0;
              return (
                <g key={d.date}>
                  {i % every === 0 ? (
                    <text x={x + bar / 2} y={H - 6} textAnchor="middle" fontSize={11} fill="var(--cm-text-dim)">
                      {fmtDay(d.date)}
                    </text>
                  ) : null}
                  {d.given > 0 ? (
                    <path d={roundTop(x, gTop, bar, base - gTop, d.refused > 0 ? 0 : 4)} fill={GIVEN} />
                  ) : null}
                  {d.refused > 0 ? (
                    <path d={roundTop(x, rTop, bar, gTop - rTop - gap, 4)} fill={REFUSED} />
                  ) : null}
                  <rect
                    x={pad.left + slot * i}
                    y={pad.top}
                    width={slot}
                    height={H - pad.top - pad.bottom}
                    fill={hover === i ? "rgba(255,255,255,0.05)" : "transparent"}
                    onMouseEnter={() => setHover(i)}
                  />
                </g>
              );
            })}
          </svg>
          {h ? (
            <div
              className="df-tooltip"
              style={tooltipAt((pad.left + slot * (hover as number) + slot / 2) / W)}
              role="status"
            >
              <strong>{fmtDay(h.date)}</strong>
              <span>{h.raised} raised</span>
              <span>
                <i style={{ background: GIVEN }} /> {h.given} given
              </span>
              <span>
                <i style={{ background: REFUSED }} /> {h.refused} refused
              </span>
            </div>
          ) : null}
        </div>
      )}
    </div>
  );
}

/** Centred over the day, but kept inside the chart at either end. */
function tooltipAt(at: number) {
  const transform = at < 0.15 ? "translateX(-8px)" : at > 0.85 ? "translateX(calc(-100% + 8px))" : "translateX(-50%)";
  return { left: `${at * 100}%`, transform };
}

/** A bar from (x, top) of height h, its top corners rounded by r (never more than it can hold). */
function roundTop(x: number, top: number, w: number, h: number, r: number): string {
  if (h <= 0) return "";
  const rr = Math.min(r, w / 2, h);
  return `M${x},${top + h} V${top + rr} Q${x},${top} ${x + rr},${top} H${x + w - rr} Q${x + w},${top} ${x + w},${top + rr} V${top + h} Z`;
}

/**
 * The dashboard's figures (ADR-049): what became of the requests raised in the period, how long people took to
 * decide, decisions by day, each purpose's outcomes, and the changes people made to consents they had given.
 */
export function Insights({ console: c, onFilter }: { console: Console; onFilter: (sts?: CnsLcySts[]) => void }) {
  const [days, setDays] = useState(30);
  const [data, setData] = useState<DfInsightsDto | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let live = true;
    setError(null);
    (async () => {
      try {
        const d = await insights.get(c.gwBase, await freshToken(c.auth), days);
        if (live) setData(d);
      } catch (err) {
        if (live) setError(friendlyError(err));
      }
    })();
    return () => {
      live = false;
    };
  }, [c.auth, c.gwBase, days]);

  const o = data?.outcomes;
  const rate = o ? decidedRate(o) : null;

  return (
    <>
      <div className="cm-toolbar df-periods" role="group" aria-label="Period">
        {PERIODS.map((p) => (
          <button
            key={p.days}
            className={`cm-btn${p.days === days ? " cm-btn-primary" : ""}`}
            aria-pressed={p.days === days}
            onClick={() => setDays(p.days)}
          >
            {p.label}
          </button>
        ))}
        <span className="cm-note" style={{ marginLeft: "auto" }}>
          Requests raised in the last {PERIODS.find((p) => p.days === days)?.label}
        </span>
      </div>
      {error ? <ErrorBanner message={error} /> : null}
      {!data || !o ? (
        error ? null : <Spinner label="Working out the figures…" />
      ) : (
        <>
          <div className="cm-summary">
            <Stat value={String(o.raised)} label="Raised" />
            <Stat
              value={String(o.given)}
              label="Given"
              note={rate != null ? `${rate}% of those who decided` : undefined}
              onClick={() => onFilter(["ISSUED", "REVOKED", "EXPIRED"])}
            />
            <Stat value={String(o.refused)} label="Refused" onClick={() => onFilter(["DENIED"])} />
            <Stat
              value={String(o.awaiting)}
              label="Awaiting a decision"
              note={o.raised ? `${share(o.awaiting, o.raised)}% of raised` : undefined}
              onClick={() => onFilter(["PENDING"])}
            />
            <Stat value={String(o.notNotified)} label="Couldn't be notified" onClick={() => onFilter(["NOTIFICATION_FAILED"])} />
          </div>

          <h3 className="cm-section-title">Time to decide</h3>
          <div className="cm-summary">
            <Stat value={fmtDuration(data.decisions.medianSeconds)} label="Median" note="from request to decision" />
            <Stat value={fmtDuration(data.decisions.p90Seconds)} label="9 in 10 decided within" />
            <Stat
              value={data.decisions.decided ? `${share(data.decisions.withinADay, data.decisions.decided)}%` : "—"}
              label="Decided within a day"
              note={`${data.decisions.decided} decided`}
            />
          </div>

          <DecisionsChart days={data.days} />

          <h3 className="cm-section-title">After consent was given</h3>
          <div className="cm-summary">
            <Stat value={String(data.changes.withdrawn)} label="Withdrawn" />
            <Stat value={String(data.changes.narrowed)} label="Narrowed or shortened" />
            <Stat value={String(data.changes.renewed)} label="Renewed" />
            <Stat value={String(data.changes.lapsed)} label="Lapsed" />
          </div>
          <p className="cm-note">Changes made in the period to any consent, whenever it was given.</p>

          {data.purposes.length ? (
            <>
              <h3 className="cm-section-title">By purpose</h3>
              <div style={{ overflowX: "auto" }}>
                <table className="cm-table">
                  <thead>
                    <tr>
                      <th>Purpose</th>
                      <th>Raised</th>
                      <th>Given</th>
                      <th>Refused</th>
                      <th>Awaiting</th>
                      <th>Given of decided</th>
                      <th>Median time to decide</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.purposes.map((p) => {
                      const r = decidedRate(p.outcomes);
                      return (
                        <tr key={p.prpsCode}>
                          <td>
                            <a href={purposeHref(p.prpsCode)}>{humanise(p.prpsCode)}</a>
                          </td>
                          <td>{p.outcomes.raised}</td>
                          <td>{p.outcomes.given}</td>
                          <td>{p.outcomes.refused}</td>
                          <td>{p.outcomes.awaiting}</td>
                          <td>{r == null ? "—" : `${r}%`}</td>
                          <td>{fmtDuration(p.medianSeconds)}</td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            </>
          ) : null}
        </>
      )}
    </>
  );
}
