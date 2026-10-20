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
import { cnsCmd, friendlyError, type CnsArtRdMdl } from "api-client";
import { RENEW_PROMPT_DAYS, dateAfter, daysLeft, humanise, shortenedUntil } from "dp-kit";
import { GW_BASE } from "../lib/config";
import { useAuth } from "../lib/auth";
import { confirm } from "../lib/confirm";
import { useConsents } from "../lib/consents";
import { Body, Button, Card, DaysPicker, Title, styles } from "./ui";

/**
 * Changing a consent in force (ADR-026 §4), as the portal does: withdraw it, stop sharing one kind of data, end it
 * sooner, or renew it. Renewing is the only way to make it last longer, from today for up to the purpose's maximum.
 */
export function Manage({ row }: { row: CnsArtRdMdl }) {
  const auth = useAuth();
  const consents = useConsents();
  const [maxDays, setMaxDays] = useState<number | null>(null);
  const [termsError, setTermsError] = useState<string | null>(null);
  const left = row.validUntil ? daysLeft(row.validUntil) : 0;
  const [sooner, setSooner] = useState(Math.max(1, left - 1));
  const [renew, setRenew] = useState(left + 1);
  const busy = consents.busy === row.cnsArtId;
  const granted = row.grantedDCatCodes ?? row.dCatCodes;

  useEffect(() => {
    auth
      .token()
      .then((token) => cnsCmd.terms(GW_BASE, token, row.cnsReqId))
      .then((t) => {
        setMaxDays(t.maxValidityDays);
        setRenew(t.maxValidityDays);
      })
      .catch((err) => setTermsError(friendlyError(err)));
  }, [auth, row.cnsReqId]);

  if (!row.validUntil) return null;
  const until = row.validUntil;
  // a renewal must end later than the consent ends now, or it isn't one
  const canRenew = maxDays !== null && maxDays > left;
  const soon = left <= RENEW_PROMPT_DAYS;

  return (
    <>
      <Card>
        <Title>Withdraw consent</Title>
        <Body dim>They must stop using your data for this purpose. You can't undo this; they would have to ask again.</Body>
        <View style={styles.row}>
          <Button
            kind="danger"
            label="Withdraw consent"
            busy={busy}
            onPress={async () => {
              if (await confirm("Withdraw consent?", "They must stop using your data for this.", "Withdraw")) {
                consents.decide(row, "withdraw");
              }
            }}
          />
        </View>
      </Card>

      {granted.length > 1 ? (
        <Card>
          <Title>Stop sharing one kind of data</Title>
          <Body dim>The rest of this consent stays.</Body>
          <View style={styles.row}>
            {granted.map((code) => (
              <Button
                key={code}
                label={`Stop sharing ${humanise(code)}`}
                disabled={busy}
                onPress={async () => {
                  if (await confirm(`Stop sharing your ${humanise(code).toLowerCase()}?`, "The rest of this consent stays.", "Stop sharing")) {
                    consents.change(row, { kind: "withdraw-dcat", dCat: code });
                  }
                }}
              />
            ))}
          </View>
        </Card>
      ) : null}

      {left > 1 ? (
        <Card>
          <Title>End it sooner</Title>
          <DaysPicker
            label="End in how many days"
            value={sooner}
            onChange={setSooner}
            min={1}
            max={left - 1}
            presets={[1, 7, 30].filter((d) => d < left)}
          />
          <Button
            kind="secondary"
            label={`End in ${sooner} day${sooner === 1 ? "" : "s"}`}
            disabled={busy}
            onPress={() => consents.change(row, { kind: "shorten", until: shortenedUntil(until, dateAfter(sooner)) })}
          />
        </Card>
      ) : null}

      <Card>
        <Title>{soon ? `Renew: it ends in ${left} day${left === 1 ? "" : "s"}` : "Renew"}</Title>
        {termsError ? <Body dim>{termsError}</Body> : null}
        {maxDays === null && !termsError ? <Body dim>Loading how long it may last…</Body> : null}
        {maxDays !== null && !canRenew ? (
          <Body dim>It already lasts as long as this purpose allows ({maxDays} days). Renew nearer the end.</Body>
        ) : null}
        {canRenew ? (
          <>
            <DaysPicker
              label="Renew for how many days from today"
              value={Math.max(renew, left + 1)}
              onChange={setRenew}
              min={left + 1}
              max={maxDays!}
              presets={[30, 90, maxDays!].filter((d) => d > left)}
            />
            <Button
              kind={soon ? "primary" : "secondary"}
              label={`Renew for ${Math.max(renew, left + 1)} days`}
              disabled={busy}
              onPress={() => consents.change(row, { kind: "renew", days: Math.max(renew, left + 1) })}
            />
          </>
        ) : null}
      </Card>
    </>
  );
}
