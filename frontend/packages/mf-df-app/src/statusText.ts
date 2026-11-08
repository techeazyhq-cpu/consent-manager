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

import type { StatusOverall, StatusState } from "api-client";

/** The headline for the platform as a whole. */
export function headline(overall: StatusOverall): string {
  switch (overall) {
    case "OPERATIONAL":
      return "All systems operational";
    case "DEGRADED":
      return "Some systems are having problems";
    case "MAJOR_OUTAGE":
      return "A major part of the platform is down";
  }
}

export const stateLabel = (state: StatusState): string =>
  state === "OPERATIONAL" ? "Operational" : state === "DEGRADED" ? "Having problems" : "Down";

/** An uptime for people: two decimals and a percent sign, or a dash when it has not been watched long enough to say. */
export const uptimeText = (percent: number | null): string => (percent === null ? "—" : `${percent.toFixed(2)}%`);

/** How stale a report is allowed to be before the page says so. */
export const STALE_AFTER_SECONDS = 120;

export function isStale(updatedAt: string, now: Date): boolean {
  return (now.getTime() - new Date(updatedAt).getTime()) / 1000 > STALE_AFTER_SECONDS;
}
