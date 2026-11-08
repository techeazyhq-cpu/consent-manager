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
import { processors, type ProcessorDto, type ProcessorUseDto } from "api-client";
import { Badge, Button, Card, ErrorBanner, Field, PageHeader, Spinner, friendlyError } from "ui-kit/src/index";
import type { Console } from "./console";
import { managesPurposes } from "./roles";
import { freshToken } from "./token";

/** Where a processor is named, in a sentence: "ORDER_UPDATES v2 (draft)". */
export function useLabel(u: ProcessorUseDto): string {
  return `${u.purpose} v${u.version}${u.status === "DRAFT" ? " (draft)" : ""}`;
}

/** What retiring a processor does, said before it is done (ADR-075). */
export function retireWarning(name: string, uses: ProcessorUseDto[]): string {
  const base = `${name} can no longer be named by a new purpose or draft. Versions that already name it stay as they are.`;
  return uses.length === 0 ? base : `${base} It is still named by ${uses.map(useLabel).join(", ")}: change those first.`;
}

/** The register of the processors a fiduciary names in its purposes (ADR-075). */
export default function Processors({ console: c }: { console: Console }) {
  const manages = managesPurposes(c.role);
  const [list, setList] = useState<ProcessorDto[] | null>(null);
  const [name, setName] = useState("");
  const [role, setRole] = useState("");
  const [country, setCountry] = useState("");
  const [contact, setContact] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [warning, setWarning] = useState<{ p: ProcessorDto; uses: ProcessorUseDto[] } | null>(null);

  const load = useCallback(async () => {
    try {
      setList(await processors.list(c.gwBase, await freshToken(c.auth)));
    } catch (err) {
      setError(friendlyError(err));
      setList((prev) => prev ?? []);
    }
  }, [c.auth, c.gwBase]);

  useEffect(() => {
    load();
  }, [load]);

  const run = async (action: (token: string) => Promise<unknown>) => {
    setBusy(true);
    setError(null);
    try {
      await action(await freshToken(c.auth));
      await load();
    } catch (err) {
      setError(friendlyError(err));
    } finally {
      setBusy(false);
    }
  };

  const add = () =>
    run(async (token) => {
      await processors.create(c.gwBase, token, { name, role, country, contactEmail: contact });
      setName("");
      setRole("");
      setCountry("");
      setContact("");
    });

  const askRetire = (p: ProcessorDto) =>
    run(async (token) => setWarning({ p, uses: await processors.uses(c.gwBase, token, p.id) }));

  return (
    <>
      <PageHeader
        title="Processors"
        subtitle="The parties that handle personal data for you. Name them in a purpose's recipients and transfers; retire one you no longer use."
      />
      {error ? <ErrorBanner message={error} /> : null}

      {manages ? (
        <Card>
          <h3 className="cm-section-title" style={{ marginTop: 0 }}>
            Add a processor
          </h3>
          <div className="cm-fields">
            <Field label="Name" value={name} onChange={setName} placeholder="e.g. Courier Ltd" />
            <Field label="What it does with the data" value={role} onChange={setRole} placeholder="Delivers your orders" />
            <Field label="Country (two letters)" value={country} onChange={setCountry} placeholder="IN" />
            <Field label="Contact email" value={contact} onChange={setContact} placeholder="privacy@courier.example" />
          </div>
          <Button variant="primary" disabled={busy || name.trim() === ""} onClick={add}>
            Add
          </Button>
        </Card>
      ) : null}

      {warning ? (
        <Card>
          <p className="cm-note">{retireWarning(warning.p.name, warning.uses)}</p>
          <Button
            variant="primary"
            disabled={busy}
            onClick={() =>
              run(async (token) => {
                await processors.retire(c.gwBase, token, warning.p.id);
                setWarning(null);
              })
            }
          >
            Retire {warning.p.name}
          </Button>{" "}
          <Button onClick={() => setWarning(null)}>Keep it</Button>
        </Card>
      ) : null}

      {list === null ? (
        <Spinner />
      ) : list.length === 0 ? (
        <p className="cm-note">No processors yet. Purposes can still name recipients as free text.</p>
      ) : (
        <Card>
          <table className="cm-table">
            <thead>
              <tr>
                <th>Name</th>
                <th>What it does</th>
                <th>Country</th>
                <th>Contact</th>
                <th>Status</th>
                {manages ? <th /> : null}
              </tr>
            </thead>
            <tbody>
              {list.map((p) => (
                <tr key={p.id}>
                  <td>{p.name}</td>
                  <td>{p.role || "—"}</td>
                  <td>{p.country || "—"}</td>
                  <td>{p.contactEmail || "—"}</td>
                  <td>
                    <Badge status={p.status} />
                  </td>
                  {manages ? (
                    <td>
                      {p.status === "ACTIVE" ? (
                        <Button disabled={busy} onClick={() => askRetire(p)}>
                          Retire
                        </Button>
                      ) : (
                        <Button disabled={busy} onClick={() => run((token) => processors.reinstate(c.gwBase, token, p.id))}>
                          Reinstate
                        </Button>
                      )}
                    </td>
                  ) : null}
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
      )}
    </>
  );
}
