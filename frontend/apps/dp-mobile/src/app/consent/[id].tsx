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

import { ScrollView, Text, View } from "react-native";
import { Redirect, Stack, router, useLocalSearchParams } from "expo-router";
import { awaitsDecision, fmtDate, humanise } from "dp-kit";
import { useAuth } from "../../lib/auth";
import { useConsents } from "../../lib/consents";
import { Decide } from "../../components/Decide";
import { Manage } from "../../components/Manage";
import { Body, Button, Card, ErrorText, StatusPill, Title, styles, useColors } from "../../components/ui";

/** One consent: what was asked and by whom, the notice, and what the person can do now. */
export default function ConsentScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const auth = useAuth();
  const consents = useConsents();
  const c = useColors();
  if (auth.state === "signed-out") return <Redirect href="/" />;
  const row = consents.byReqId(id);
  if (!row) {
    return (
      <View style={{ padding: 16 }}>
        <Body dim>{consents.rows ? "This consent isn't yours, or no longer exists." : "Loading…"}</Body>
      </View>
    );
  }
  const org = consents.orgName(row.dfId);
  const granted = row.grantedDCatCodes ?? row.dCatCodes;
  const notShared = row.lcySts === "ISSUED" ? row.dCatCodes.filter((d) => !granted.includes(d)) : [];

  return (
    <ScrollView contentContainerStyle={{ padding: 16 }}>
      <Stack.Screen options={{ title: org }} />
      <Card>
        <Text style={{ color: c.dim, fontSize: 14 }}>{org} asks for</Text>
        <Title>{row.ntcTitle || humanise(row.prpsCode)}</Title>
        <StatusPill sts={row.lcySts} />
        <Body dim style={{ marginTop: 8 }}>
          {awaitsDecision(row.lcySts) ? "Data asked for: " : "Data shared: "}
          {(row.lcySts === "ISSUED" ? granted : row.dCatCodes).map(humanise).join(", ")}
          {notShared.length ? `. Not shared: ${notShared.map(humanise).join(", ")}` : ""}
        </Body>
        {row.lcySts === "ISSUED" && row.validUntil ? <Body dim>Valid until {fmtDate(row.validUntil)}</Body> : null}
      </Card>

      <Card>
        <Title>What they told you</Title>
        <Body>{row.ntcText || "This request was sent without a notice you can read here."}</Body>
      </Card>

      <ErrorText message={consents.error} />
      {awaitsDecision(row.lcySts) ? <Decide row={row} /> : null}
      {row.lcySts === "ISSUED" ? <Manage row={row} /> : null}

      <View style={[styles.row, { marginBottom: 32 }]}>
        <Button
          label="History and receipts"
          onPress={() => router.push({ pathname: "/history/[id]", params: { id: row.cnsReqId } })}
          hint="Every decision on this consent, with its signed receipt"
        />
      </View>
    </ScrollView>
  );
}
