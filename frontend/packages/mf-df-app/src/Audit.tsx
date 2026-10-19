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

import { useCallback, useEffect, useState } from "react";
import { aud, type AudActivityRow } from "api-client";
import { AuditTable, Button, ErrorBanner, PageHeader, Spinner, friendlyError } from "ui-kit/src/index";
import type { Console } from "./console";
import { readsAudit } from "./roles";
import { freshToken } from "./token";

type Category = "ALL" | "PLATFORM" | "CONSENT";

const CATEGORIES: { key: Category; label: string }[] = [
  { key: "ALL", label: "Everything" },
  { key: "PLATFORM", label: "Changes to your account" },
  { key: "CONSENT", label: "Consent requests and decisions" },
];

const PAGE = 50;

/**
 * The organisation's audit (ADR-040): who changed what here (team, plan, lists sent), and every consent request and
 * decision, from the tamper-evident ledger. Owners, Administrators and Auditors see it.
 */
export default function Audit({ console: c }: { console: Console }) {
  const [category, setCategory] = useState<Category>("ALL");
  const [rows, setRows] = useState<AudActivityRow[] | null>(null);
  const [more, setMore] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(
    async (before?: number) => {
      setError(null);
      try {
        const page = await aud.activity(c.gwBase, await freshToken(c.auth), { category, before, limit: PAGE });
        setRows((prev) => (before ? [...(prev ?? []), ...page] : page));
        setMore(page.length === PAGE);
      } catch (err) {
        setError(friendlyError(err));
        setRows((prev) => prev ?? []);
      }
    },
    [c.auth, c.gwBase, category],
  );

  useEffect(() => {
    if (!readsAudit(c.role)) return;
    setRows(null);
    load();
  }, [load, c.role]);

  if (!readsAudit(c.role)) {
    return (
      <>
        <PageHeader title="Audit" />
        <p className="cm-note">Owners, Administrators and Auditors see the audit.</p>
      </>
    );
  }

  return (
    <>
      <PageHeader
        title="Audit"
        subtitle="Who changed what in your organisation's account, and every consent request and decision. Entries can't be changed or removed."
      />
      <div className="cm-chips" role="group" aria-label="Show">
        {CATEGORIES.map((cat) => (
          <button key={cat.key} aria-pressed={category === cat.key} onClick={() => setCategory(cat.key)}>
            {cat.label}
          </button>
        ))}
        <button onClick={() => load()}>Refresh</button>
      </div>
      {error ? <ErrorBanner message={error} /> : null}
      {rows === null ? (
        <Spinner />
      ) : rows.length === 0 ? (
        <p className="cm-note">Nothing recorded yet.</p>
      ) : (
        <AuditTable rows={rows} />
      )}
      {more ? (
        <p>
          <Button onClick={() => load(rows?.[rows.length - 1]?.seq)}>Older entries</Button>
        </p>
      ) : null}
    </>
  );
}
