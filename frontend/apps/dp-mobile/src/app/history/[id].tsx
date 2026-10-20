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
import { ActivityIndicator, ScrollView, Text, View } from "react-native";
import { Redirect, useLocalSearchParams } from "expo-router";
import { friendlyError, records, type ConsentRecordDoc, type ReceiptView } from "api-client";
import { EVENT_LABELS, changeLabel, fmtDate, humanise } from "dp-kit";
import { GW_BASE } from "../../lib/config";
import { useAuth } from "../../lib/auth";
import { shareText } from "../../lib/share";
import { Body, Button, Card, ErrorText, Title, useColors } from "../../components/ui";

/**
 * The consent's ISO/IEC TS 27560 record (ADR-046): every event, and the receipt signed for each, to keep or share.
 * A receipt verifies offline against the published receipt keys.
 */
export default function HistoryScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const auth = useAuth();
  const c = useColors();
  const [doc, setDoc] = useState<ConsentRecordDoc | null>(null);
  const [receipts, setReceipts] = useState<ReceiptView[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (auth.state !== "signed-in") return;
    let live = true;
    auth
      .token()
      .then((token) => Promise.all([records.get(GW_BASE, token, id), records.receipts(GW_BASE, token, id)]))
      .then(([d, r]) => {
        if (!live) return;
        setDoc(d);
        setReceipts(r);
      })
      .catch((err) => live && setError(friendlyError(err)));
    return () => {
      live = false;
    };
  }, [auth, id]);

  if (auth.state === "signed-out") return <Redirect href="/" />;
  if (error) return <View style={{ padding: 16 }}><ErrorText message={error} /></View>;
  if (!doc) return <ActivityIndicator style={{ marginTop: 40 }} color={c.primary} accessibilityLabel="Loading" />;

  const receiptFor = (seq: number) => receipts.find((r) => r.forEvent === seq);
  const purpose = doc.processing.purposes[0];
  return (
    <ScrollView contentContainerStyle={{ padding: 16 }}>
      <Card>
        <Title>{purpose?.name ?? humanise(purpose?.code ?? "")}</Title>
        <Body dim>
          With {doc.processing.controller.name}. Record {doc.header.recordId.slice(0, 8)}, kept to ISO/IEC TS 27560.
        </Body>
      </Card>
      {doc.events.map((e) => {
        const r = receiptFor(e.seq);
        const detail = changeLabel(e.change);
        return (
          <Card key={e.seq}>
            <Text style={{ color: c.dim, fontSize: 14 }}>
              {fmtDate(e.time)} · {new Date(e.time).toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit" })}
            </Text>
            <Title>{EVENT_LABELS[e.type]}</Title>
            {detail ? <Body>{detail}</Body> : null}
            {e.personalData?.length ? <Body dim>Data: {e.personalData.map(humanise).join(", ")}</Body> : null}
            {e.validUntil ? <Body dim>Valid until {fmtDate(e.validUntil)}</Body> : null}
            {r ? (
              <View style={{ marginTop: 8 }}>
                <Button
                  label="Save the signed receipt"
                  hint="A file you can keep, and verify with the published receipt keys"
                  onPress={() => shareText(`receipt-${doc.header.recordId.slice(0, 8)}-${e.seq}.jws`, r.jws, "application/jose")}
                />
              </View>
            ) : (
              <Body dim>Its receipt is being signed.</Body>
            )}
          </Card>
        );
      })}
    </ScrollView>
  );
}

