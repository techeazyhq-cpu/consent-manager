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
import { View } from "react-native";
import { cnsCmd, friendlyError, type CnsArtRdMdl, type CnsReqTermsDto } from "api-client";
import { choiceProblem, giveLabel, humanise } from "dp-kit";
import { GW_BASE } from "../lib/config";
import { useAuth } from "../lib/auth";
import { useConsents } from "../lib/consents";
import { Body, Button, Card, Checkbox, DaysPicker, ErrorText, Title, styles } from "./ui";

/**
 * Deciding on a request, by the same rules as the web portal (ADR-026, ADR-028, ADR-032 §3): each kind of data
 * asked for, none ticked, with what refusing it means when it's needed; how long consent lasts, opening on what was
 * asked and never past the purpose's maximum. Declining is one press, as easy as giving.
 */
export function Decide({ row }: { row: CnsArtRdMdl }) {
  const auth = useAuth();
  const consents = useConsents();
  const [terms, setTerms] = useState<CnsReqTermsDto | null>(null);
  const [failed, setFailed] = useState<string | null>(null);
  const [chosen, setChosen] = useState<string[]>([]);
  const [days, setDays] = useState(30);
  const busy = consents.busy === (row.cnsArtId ?? row.cnsReqId);

  useEffect(() => {
    let live = true;
    auth
      .token()
      .then((token) => cnsCmd.terms(GW_BASE, token, row.cnsReqId))
      .then((t) => {
        if (!live) return;
        setTerms(t);
        setDays(t.requestedValidityDays);
      })
      .catch((err) => live && setFailed(friendlyError(err)));
    return () => {
      live = false;
    };
  }, [auth, row.cnsReqId]);

  const decline = (
    <Button label="Decline" disabled={busy} onPress={() => consents.decide(row, "decline")} hint="Refuses this request" />
  );
  if (failed) {
    return (
      <Card>
        <Body dim>The details of this request couldn't be loaded: {failed}</Body>
        <View style={styles.row}>{decline}</View>
      </Card>
    );
  }
  if (!terms) return <Body dim>Loading what is asked…</Body>;

  const problem = choiceProblem(chosen, days, terms.maxValidityDays);
  const toggle = (code: string, on: boolean) =>
    setChosen((prev) => (on ? [...prev, code] : prev.filter((c) => c !== code)));

  return (
    <Card>
      <Title>Which of your data you agree to share</Title>
      {terms.dCats.map((d) => (
        <Checkbox
          key={d.code}
          checked={chosen.includes(d.code)}
          onChange={(on) => toggle(d.code, on)}
          label={`${humanise(d.code)}${d.necessary ? " (needed)" : ""}`}
          detail={d.necessary && d.consequence ? `If you don't share it: ${d.consequence}` : undefined}
        />
      ))}

      <Title>How long</Title>
      <DaysPicker
        label="How long consent lasts"
        value={days}
        onChange={setDays}
        min={1}
        max={terms.maxValidityDays}
        presets={[terms.requestedValidityDays, 30, terms.maxValidityDays]}
      />
      <Body dim>
        Asked for {terms.requestedValidityDays} days. You can choose up to {terms.maxValidityDays}, and withdraw at any
        time.
        {terms.recipients.length ? ` They share it with: ${terms.recipients.join(", ")}.` : ""}
        {terms.retentionDays ? ` They keep it for ${terms.retentionDays} days.` : ""}
      </Body>

      <ErrorText message={problem && chosen.length > 0 ? problem : null} />
      <View style={styles.row}>
        <Button
          kind="primary"
          label={giveLabel(chosen.length, terms.dCats.length)}
          disabled={problem !== null}
          busy={busy}
          onPress={() => consents.decide(row, "give", { dCatCodes: chosen, validityDays: days })}
        />
        {decline}
      </View>
    </Card>
  );
}
