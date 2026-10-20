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

import { useState } from "react";
import { ScrollView, View } from "react-native";
import { Redirect, router } from "expo-router";
import { ApiError, friendlyError } from "api-client";
import { GW_BASE } from "../lib/config";
import { useAuth } from "../lib/auth";
import { forgetThisPhone } from "../lib/pushDevice";
import { shareText } from "../lib/share";
import { PushToggle } from "../components/PushToggle";
import { Body, Button, Card, ErrorText, Title, styles } from "../components/ui";

/** Who is signed in; notifications on this phone; every record and receipt to download (ADR-046); signing out. */
export default function AccountScreen() {
  const auth = useAuth();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  if (auth.state === "signed-out") return <Redirect href="/" />;

  const exportAll = async () => {
    setBusy(true);
    setError(null);
    try {
      const res = await fetch(`${GW_BASE}/api/cns-cmd/records/export`, {
        headers: { Authorization: `Bearer ${await auth.token()}` },
      });
      if (!res.ok) throw new ApiError(res.status, await res.json().catch(() => null));
      await shareText(`my-consent-records-${new Date().toISOString().slice(0, 10)}.jsonld`, await res.text(), "application/ld+json");
    } catch (err) {
      setError(friendlyError(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <ScrollView contentContainerStyle={{ padding: 16 }}>
      <Card>
        <Title>Signed in</Title>
        <Body>{auth.person?.username ?? auth.person?.email ?? "You"}</Body>
      </Card>
      <PushToggle />
      <Card>
        <Title>Your records</Title>
        <Body dim>
          Every consent's record and every signed receipt, in one file (JSON-LD, ISO/IEC TS 27560). Keep it, or take it
          elsewhere.
        </Body>
        <ErrorText message={error} />
        <View style={styles.row}>
          <Button kind="primary" label="Download all my records" busy={busy} onPress={exportAll} />
        </View>
      </Card>
      <Card>
        <Title>Sign out</Title>
        <Body dim>Signs out of this phone and stops its notifications. Your consents stay as they are.</Body>
        <View style={styles.row}>
          <Button
            label="Sign out"
            onPress={async () => {
              // while the session still holds, so the next person on this phone doesn't get these notifications
              await auth
                .token()
                .then(forgetThisPhone)
                .catch(() => undefined);
              await auth.signOut();
              router.replace("/");
            }}
          />
        </View>
      </Card>
    </ScrollView>
  );
}
