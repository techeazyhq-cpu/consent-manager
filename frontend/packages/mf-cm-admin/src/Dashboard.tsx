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

import { useEffect, useMemo, useState } from "react";
import { dfReg, type DfListItemRespDto } from "api-client";
import { Badge, Button, ErrorBanner, PageHeader, Spinner, friendlyError } from "ui-kit/src/index";
import DfLogo from "./DfLogo";
import { tenantHref } from "./routes";
import { freshToken, type Session } from "./session";

type Filter = "ALL" | "ACTIVE" | "PENDING";

/** Every data fiduciary as a logo tile; a tile opens everything about that tenant. */
export default function Dashboard({ session }: { session: Session }) {
  const [dfs, setDfs] = useState<DfListItemRespDto[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [query, setQuery] = useState("");
  const [filter, setFilter] = useState<Filter>("ALL");

  const load = async () => {
    setError(null);
    try {
      const list = await dfReg.list(session.gwBase, await freshToken(session.auth));
      setDfs([...list].sort((a, b) => a.name.localeCompare(b.name)));
    } catch (err) {
      setError(friendlyError(err));
      setDfs((prev) => prev ?? []);
    }
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const shown = useMemo(() => {
    const q = query.trim().toLowerCase();
    return (dfs ?? []).filter(
      (df) =>
        (filter === "ALL" || df.sts === filter) &&
        (!q || [df.name, df.legalName, df.contactEmail, df.industry].some((v) => v?.toLowerCase().includes(q))),
    );
  }, [dfs, query, filter]);

  const count = (sts?: Filter) => (dfs ?? []).filter((df) => !sts || df.sts === sts).length;

  return (
    <>
      <PageHeader title="Dashboard" subtitle="The data fiduciaries on the platform. Open one to see everything about it." />
      {error ? <ErrorBanner message={error} /> : null}

      <div className="adm-summary">
        <div className="adm-stat">
          <b>{dfs ? count() : "–"}</b>
          <span>Tenants</span>
        </div>
        <div className="adm-stat">
          <b>{dfs ? count("ACTIVE") : "–"}</b>
          <span>Active</span>
        </div>
        <div className="adm-stat">
          <b>{dfs ? count("PENDING") : "–"}</b>
          <span>Awaiting activation</span>
        </div>
      </div>

      <div className="adm-toolbar">
        <input
          type="search"
          placeholder="Search by name, email or industry"
          aria-label="Search tenants"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />
        <select aria-label="Show" value={filter} onChange={(e) => setFilter(e.target.value as Filter)}>
          <option value="ALL">All tenants</option>
          <option value="ACTIVE">Active</option>
          <option value="PENDING">Awaiting activation</option>
        </select>
        <Button onClick={load}>Refresh</Button>
        <a className="cm-btn cm-btn-primary" href="#/onboard" style={{ textDecoration: "none" }}>
          Onboard tenant
        </a>
      </div>

      {dfs === null ? (
        <Spinner label="Loading tenants…" />
      ) : shown.length === 0 ? (
        <p className="adm-note">
          {dfs.length === 0 ? "No tenants yet. Onboard the first one." : "No tenants match your search."}
        </p>
      ) : (
        <div className="adm-tiles">
          {shown.map((df) => (
            <a key={df.id} className="adm-tile" href={tenantHref(df.id)} style={{ textDecoration: "none" }}>
              <DfLogo session={session} dfId={df.id} name={df.name} />
              <div className="adm-tile-name">{df.name}</div>
              <div className="adm-tile-meta" title={df.contactEmail}>
                {df.contactEmail}
              </div>
              <div className="adm-tile-meta">{[df.industry, df.subPlnName ?? "FREE"].filter(Boolean).join(" · ")}</div>
              <Badge status={df.sts} />
            </a>
          ))}
        </div>
      )}
    </>
  );
}
