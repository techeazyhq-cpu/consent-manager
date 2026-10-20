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
import { Linking, View } from "react-native";
import { friendlyError } from "api-client";
import { useAuth } from "../lib/auth";
import { PushBlockedError, checkThisPhone, forgetThisPhone, pushMode, registerThisPhone } from "../lib/pushDevice";
import { Body, Button, Card, ErrorText, Title, styles } from "./ui";

type State = "loading" | "on" | "off" | "busy" | "blocked";

/** "Notify me" on the Account screen: new consent requests are pushed to this phone. Off until the person opts in. */
export function PushToggle() {
  const auth = useAuth();
  const [state, setState] = useState<State>("loading");
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (pushMode === "off") return;
    let cancelled = false;
    auth
      .token()
      .then(checkThisPhone)
      .then((s) => {
        if (!cancelled) setState(s);
      })
      .catch((err) => {
        if (cancelled) return;
        setState("off");
        setError(friendlyError(err));
      });
    return () => {
      cancelled = true;
    };
    // once, when the screen opens
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  if (pushMode === "off") return null;

  const turnOn = async () => {
    setState("busy");
    setError(null);
    try {
      await registerThisPhone(await auth.token());
      setState("on");
    } catch (err) {
      if (err instanceof PushBlockedError) {
        setState("blocked");
      } else {
        setState("off");
        setError(friendlyError(err));
      }
    }
  };

  const turnOff = async () => {
    setState("busy");
    setError(null);
    try {
      await forgetThisPhone(await auth.token());
      setState("off");
    } catch (err) {
      setState("on");
      setError(friendlyError(err));
    }
  };

  const on = state === "on";
  return (
    <Card>
      <Title>Notifications</Title>
      <Body dim>
        {state === "blocked"
          ? "Notifications are turned off for this app. Allow them in your phone's settings, then come back here."
          : on
            ? "New consent requests are sent to this phone. The notification only says that a request came; open it to see the request."
            : "Get a notification on this phone when an organisation asks for your consent."}
      </Body>
      {pushMode === "dev" ? (
        <Body dim>
          This build has no Firebase, so notifications go to the local push catcher. While the app is open, they show
          here as a banner.
        </Body>
      ) : null}
      <ErrorText message={error} />
      <View style={styles.row}>
        {state === "blocked" ? (
          <Button label="Open settings" onPress={() => Linking.openSettings()} hint="Opens this app's settings" />
        ) : (
          <Button
            kind={on ? "secondary" : "primary"}
            label={state === "loading" ? "Notifications…" : on ? "Turn notifications off" : "Notify me"}
            busy={state === "busy"}
            disabled={state === "loading"}
            onPress={on ? turnOff : turnOn}
            hint={on ? "Stops notifications on this phone" : "Sends a notification here when an organisation asks for consent"}
          />
        )}
      </View>
    </Card>
  );
}
