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

import { Stack } from "expo-router";
import { StatusBar } from "expo-status-bar";
import { AuthProvider } from "../lib/auth";
import { ConsentsProvider } from "../lib/consents";
import { useColors } from "../components/ui";

function Screens() {
  const c = useColors();
  return (
    <Stack
      screenOptions={{
        headerStyle: { backgroundColor: c.surface },
        headerTintColor: c.text,
        contentStyle: { backgroundColor: c.bg },
      }}
    >
      <Stack.Screen name="index" options={{ title: "Your consents" }} />
      <Stack.Screen name="consent/[id]" options={{ title: "Consent" }} />
      <Stack.Screen name="history/[id]" options={{ title: "History and receipts" }} />
      <Stack.Screen name="account" options={{ title: "Account" }} />
      <Stack.Screen name="auth" options={{ headerShown: false }} />
    </Stack>
  );
}

/** The DP mobile app (ADR-032): the same rights and journeys as the web portal, for principals only. */
export default function RootLayout() {
  return (
    <AuthProvider>
      <ConsentsProvider>
        <StatusBar style="auto" />
        <Screens />
      </ConsentsProvider>
    </AuthProvider>
  );
}
