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

import { useEffect, useRef, useState } from "react";
import { AccessibilityInfo, AppState, Pressable, Text, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { useAuth } from "../lib/auth";
import { PUSH_CATCHER } from "../lib/config";
import { useConsents } from "../lib/consents";
import { openPush } from "../lib/openPush";
import { catcherQuery, freshPushes, nextSince, type CaughtPush } from "../lib/push";
import { devTokenIfOn, pushMode } from "../lib/pushDevice";
import { styles, useColors } from "./ui";

const POLL_MS = 3000;
const SHOW_MS = 10000;

/**
 * The dev push relay (ADR-051): in development mode no push reaches the phone, so while the app is open it polls
 * notification-service's catcher for pushes sent to this phone's stand-in token and shows each as a banner. Tapping
 * it does what tapping a real notification does. Only in development mode, only signed in with notifications on.
 *
 * The first poll only notes what the catcher already holds, so old pushes aren't replayed; later polls start from
 * the newest push seen, by the catcher's clock, so the phone's own clock doesn't matter.
 */
export function DevPushRelay() {
  const auth = useAuth();
  const consents = useConsents();
  const c = useColors();
  const insets = useSafeAreaInsets();
  const [banner, setBanner] = useState<CaughtPush | null>(null);
  const reload = useRef(consents.reload);
  reload.current = consents.reload;
  const signedIn = auth.state === "signed-in";

  useEffect(() => {
    if (pushMode !== "dev" || !signedIn) return;
    let token: string | null = null;
    let since = "";
    const shown = new Set<string>();
    let busy = false;

    const poll = async () => {
      if (busy || AppState.currentState !== "active") return;
      busy = true;
      try {
        const current = await devTokenIfOn();
        if (current !== token) {
          // turned on, off, or a new stand-in token: start over from what the catcher holds now
          token = current;
          since = "";
          shown.clear();
        }
        if (!token) return;
        const priming = since === "";
        const res = await fetch(catcherQuery(PUSH_CATCHER, token, since));
        if (!res.ok) return;
        const caught = (await res.json()) as CaughtPush[];
        const fresh = freshPushes(caught, token, shown);
        fresh.forEach((p) => shown.add(p.id));
        since = nextSince(caught, since || new Date(0).toISOString());
        const latest = fresh[fresh.length - 1];
        if (!priming && latest) {
          setBanner(latest);
          AccessibilityInfo.announceForAccessibility(`${latest.subject ?? "Notification"}: ${latest.body ?? ""}`);
          reload.current().catch(() => undefined);
        }
      } catch {
        // the catcher isn't running or reachable (e.g. a phone without EXPO_PUBLIC_PUSH_CATCHER_URL): try again later
      } finally {
        busy = false;
      }
    };

    poll();
    const timer = setInterval(poll, POLL_MS);
    return () => clearInterval(timer);
  }, [signedIn]);

  useEffect(() => {
    if (!banner) return;
    const timer = setTimeout(() => setBanner(null), SHOW_MS);
    return () => clearTimeout(timer);
  }, [banner]);

  if (!banner) return null;
  return (
    <View pointerEvents="box-none" style={{ position: "absolute", top: insets.top + 8, left: 12, right: 12 }}>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={`${banner.subject ?? "Notification"}: ${banner.body ?? ""}`}
        accessibilityHint="Opens the request"
        onPress={() => {
          const data = banner.data;
          setBanner(null);
          openPush(data, reload.current);
        }}
        style={({ pressed }) => [
          styles.card,
          {
            backgroundColor: c.surface,
            borderColor: c.primary,
            opacity: pressed ? 0.85 : 1,
            shadowColor: "#000",
            shadowOpacity: 0.2,
            shadowRadius: 8,
            elevation: 6,
          },
        ]}
      >
        <Text style={[styles.small, { color: c.dim }]}>Development push · tap to open</Text>
        <Text style={[styles.title, { color: c.text, marginBottom: 2 }]}>{banner.subject ?? "Notification"}</Text>
        {banner.body ? <Text style={[styles.body, { color: c.text }]}>{banner.body}</Text> : null}
      </Pressable>
    </View>
  );
}
