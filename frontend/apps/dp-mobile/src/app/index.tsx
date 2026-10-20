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

import { ActivityIndicator, FlatList, Pressable, RefreshControl, Text, View } from "react-native";
import { Link, Stack, router } from "expo-router";
import type { CnsArtRdMdl } from "api-client";
import { awaitsDecision, daysLeft, fmtDate, humanise } from "dp-kit";
import { useAuth } from "../lib/auth";
import { useConsents } from "../lib/consents";
import { isPrincipal } from "../lib/session";
import { Body, Button, Card, ErrorText, StatusPill, Title, styles, useColors } from "../components/ui";

function SignIn() {
  const auth = useAuth();
  const c = useColors();
  return (
    <View style={{ flex: 1, padding: 24, justifyContent: "center", backgroundColor: c.bg }}>
      <Text accessibilityRole="header" style={{ fontSize: 28, fontWeight: "800", color: c.text, marginBottom: 8 }}>
        Consent Manager
      </Text>
      <Body dim style={{ marginBottom: 24 }}>
        See who asks for your data and why. Give, change or withdraw your consent, and keep the signed record of every
        decision.
      </Body>
      {auth.state === "starting" ? (
        <ActivityIndicator accessibilityLabel="Connecting" color={c.primary} />
      ) : (
        <Button kind="primary" label="Sign in" onPress={auth.signIn} hint="With a code sent to your email or phone" />
      )}
      <ErrorText message={auth.error} />
    </View>
  );
}

function ConsentRow({ row }: { row: CnsArtRdMdl }) {
  const consents = useConsents();
  const c = useColors();
  const left = row.lcySts === "ISSUED" && row.validUntil ? daysLeft(row.validUntil) : null;
  const purpose = row.ntcTitle || humanise(row.prpsCode);
  const org = consents.orgName(row.dfId);
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={`${org}: ${purpose}`}
      accessibilityHint="Opens this consent"
      onPress={() => router.push({ pathname: "/consent/[id]", params: { id: row.cnsReqId } })}
      style={({ pressed }) => ({ opacity: pressed ? 0.8 : 1 })}
    >
      <Card>
        <Text style={{ color: c.dim, fontSize: 14 }}>{org}</Text>
        <Text style={{ color: c.text, fontSize: 17, fontWeight: "600", marginVertical: 4 }}>{purpose}</Text>
        <View style={{ flexDirection: "row", alignItems: "center", gap: 10, flexWrap: "wrap" }}>
          <StatusPill sts={row.lcySts} />
          <Text style={{ color: c.dim, fontSize: 14 }}>
            {awaitsDecision(row.lcySts)
              ? `Asked ${fmtDate(row.createdAt)}`
              : left !== null
                ? `Ends in ${left} day${left === 1 ? "" : "s"}`
                : `Updated ${fmtDate(row.updatedAt)}`}
          </Text>
        </View>
      </Card>
    </Pressable>
  );
}

function Home() {
  const auth = useAuth();
  const consents = useConsents();
  const c = useColors();

  if (auth.person && !isPrincipal(auth.person)) {
    return (
      <View style={{ padding: 24 }}>
        <Title>This account isn't a personal one</Title>
        <Body dim>
          {auth.person.username ?? "This account"} belongs to an organisation or the platform's staff. Sign out and sign
          in with the email or phone number you use as yourself.
        </Body>
        <View style={styles.row}>
          <Button label="Sign out" onPress={auth.signOut} />
        </View>
      </View>
    );
  }
  if (!consents.rows) return <ActivityIndicator style={{ marginTop: 40 }} color={c.primary} accessibilityLabel="Loading" />;

  const awaiting = consents.awaiting;
  const soon = consents.endingSoon;
  const rest = consents.sorted.filter((r) => !awaitsDecision(r.lcySts));
  const sections: { key: string; title: string; rows: CnsArtRdMdl[] }[] = [
    { key: "awaiting", title: `Waiting for you (${awaiting.length})`, rows: awaiting },
    { key: "soon", title: "Ending soon: renew or let them end", rows: soon },
    { key: "rest", title: "Your consents", rows: rest },
  ].filter((s) => s.rows.length > 0);

  return (
    <FlatList
      contentContainerStyle={{ padding: 16 }}
      data={sections}
      keyExtractor={(s) => s.key}
      refreshControl={<RefreshControl refreshing={consents.refreshing} onRefresh={consents.reload} />}
      ListHeaderComponent={<ErrorText message={consents.error} />}
      ListEmptyComponent={
        <Card>
          <Title>Nothing yet</Title>
          <Body dim>When an organisation asks for your consent, it appears here, and you'll decide what to share.</Body>
        </Card>
      }
      renderItem={({ item }) => (
        <View>
          <Text accessibilityRole="header" style={{ color: c.text, fontSize: 16, fontWeight: "700", marginVertical: 8 }}>
            {item.title}
          </Text>
          {item.rows.map((r) => (
            <ConsentRow key={`${item.key}-${r.cnsReqId}`} row={r} />
          ))}
        </View>
      )}
    />
  );
}

export default function Index() {
  const auth = useAuth();
  const c = useColors();
  return (
    <>
      <Stack.Screen
        options={{
          headerShown: auth.state === "signed-in",
          headerRight: () => (
            <Link href="/account" accessibilityLabel="Account" style={{ color: c.primary, fontSize: 16, padding: 12 }}>
              Account
            </Link>
          ),
        }}
      />
      {auth.state === "signed-in" ? <Home /> : <SignIn />}
    </>
  );
}
