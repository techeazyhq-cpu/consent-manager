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

import type { CnsLcySts } from "api-client";

/** A code such as {@code ORDER_FULFILLMENT} as words a person reads: "Order fulfillment". */
export function humanise(code: string): string {
  const words = code.replace(/[_-]+/g, " ").trim().toLowerCase();
  return words.charAt(0).toUpperCase() + words.slice(1);
}

/**
 * Where a consent stands, in the person's terms. A request whose notification failed is still theirs to decide, so
 * it reads as waiting for them.
 */
export const STATUS: Record<CnsLcySts, { label: string; badge: string }> = {
  PENDING: { label: "Awaiting your decision", badge: "PENDING" },
  NOTIFICATION_FAILED: { label: "Awaiting your decision", badge: "PENDING" },
  ISSUED: { label: "Given", badge: "ACTIVE" },
  DENIED: { label: "Declined", badge: "DENIED" },
  REVOKED: { label: "Withdrawn", badge: "REVOKED" },
  EXPIRED: { label: "Expired", badge: "EXPIRED" },
};

export const awaitsDecision = (sts: CnsLcySts) => sts === "PENDING" || sts === "NOTIFICATION_FAILED";

export const fmtDate = (iso?: string | null) =>
  iso ? new Date(iso).toLocaleDateString(undefined, { day: "numeric", month: "short", year: "numeric" }) : null;
