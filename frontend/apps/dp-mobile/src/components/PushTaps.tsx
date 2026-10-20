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

import { useEffect, useRef } from "react";
import { router } from "expo-router";
import { useAuth } from "../lib/auth";
import { useConsents } from "../lib/consents";
import * as fcm from "../lib/fcm";
import { cnsReqIdOf } from "../lib/push";
import { pushMode, tokenChanged } from "../lib/pushDevice";

/**
 * What a push does once it arrives. A tap opens the request it names, or the list of requests; either way the
 * list is fetched again first, since the push itself carries no details (ADR-032 §4). A push that arrives while
 * the app is open just refreshes the list. Nothing happens while signed out: the person signs in first.
 */
export function PushTaps() {
  const auth = useAuth();
  const consents = useConsents();
  // the listeners outlive renders, so they read the current auth and consents through refs
  const latest = useRef({ auth, consents });
  latest.current = { auth, consents };
  const signedIn = auth.state === "signed-in";

  useEffect(() => {
    if (pushMode !== "fcm" || !signedIn) return;
    const reload = () => latest.current.consents.reload().catch(() => undefined);
    const open = (message: fcm.PushMessage | null) => {
      if (!message) return;
      reload();
      const id = cnsReqIdOf(message.data);
      if (id) router.navigate({ pathname: "/consent/[id]", params: { id } });
      else router.navigate("/");
    };
    fcm.initialTap().then(open, () => undefined);
    const offs = [
      fcm.onTap(open),
      fcm.onForeground(reload),
      fcm.onTokenChange(() =>
        latest.current.auth
          .token()
          .then(tokenChanged)
          .catch(() => undefined),
      ),
    ];
    return () => offs.forEach((off) => off());
  }, [signedIn]);

  return null;
}
