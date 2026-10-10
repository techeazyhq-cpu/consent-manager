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
import { ApiError, ntf, type AuthInfo } from "api-client";
import { friendlyError } from "ui-kit/src/index";
import {
  PushBlockedError,
  notificationsBlocked,
  obtainToken,
  pushMode,
  pushSupported,
  releaseToken,
  storeDeviceId,
  storedDeviceId,
} from "./push";

type State = "loading" | "on" | "off" | "busy" | "blocked";

interface Props {
  auth: AuthInfo;
  gwBase: string;
  onError: (message: string | null) => void;
}

/** "Notify me": registers this browser so new consent requests are pushed to it. */
export default function PushToggle({ auth, gwBase, onError }: Props) {
  const [state, setState] = useState<State>("loading");

  useEffect(() => {
    if (!pushSupported()) return;
    let cancelled = false;
    (async () => {
      const id = storedDeviceId();
      const devices = await ntf.listPushDvcs(gwBase, auth.token);
      const registered = id !== null && devices.some((d) => d.id === id);
      if (!registered) {
        storeDeviceId(null);
        if (!cancelled) setState(notificationsBlocked() ? "blocked" : "off");
        return;
      }
      // Push tokens can rotate; re-registering on each visit keeps the server's copy current.
      if (pushMode === "dev" || Notification.permission === "granted") {
        const dvc = await ntf.registerPushDvc(gwBase, auth.token, { token: await obtainToken(), platform: "WEB" });
        storeDeviceId(dvc.id);
      }
      if (!cancelled) setState("on");
    })().catch((err) => {
      if (!cancelled) setState("off");
      onError(friendlyError(err));
    });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  if (!pushSupported()) return null;

  const turnOn = async () => {
    setState("busy");
    onError(null);
    try {
      const dvc = await ntf.registerPushDvc(gwBase, auth.token, { token: await obtainToken(), platform: "WEB" });
      storeDeviceId(dvc.id);
      setState("on");
    } catch (err) {
      if (err instanceof PushBlockedError) {
        setState("blocked");
      } else {
        setState("off");
        onError(friendlyError(err));
      }
    }
  };

  const turnOff = async () => {
    setState("busy");
    onError(null);
    try {
      await forgetThisBrowser(auth, gwBase);
      setState("off");
    } catch (err) {
      setState("on");
      onError(friendlyError(err));
    }
  };

  if (state === "blocked") {
    return (
      <span className="cm-push-toggle" title="Allow notifications for this site in your browser settings, then reload.">
        Notifications blocked
      </span>
    );
  }

  const on = state === "on";
  return (
    <button
      className="cm-push-toggle"
      aria-pressed={on}
      disabled={state === "loading" || state === "busy"}
      onClick={on ? turnOff : turnOn}
      title={on ? "New consent requests are pushed to this browser" : "Get a notification here when an organization asks for consent"}
    >
      {state === "loading" ? "Notifications…" : on ? "Notifications on" : "Notify me"}
    </button>
  );
}

/**
 * Removes this browser's device, e.g. when the DP signs out, so a shared computer stops getting their
 * notifications. A device already gone (404) counts as removed.
 */
export async function forgetThisBrowser(auth: AuthInfo, gwBase: string): Promise<void> {
  const id = storedDeviceId();
  if (id === null) return;
  try {
    await ntf.removePushDvc(gwBase, auth.token, id);
  } catch (err) {
    if (!(err instanceof ApiError && err.status === 404)) throw err;
  }
  storeDeviceId(null);
  await releaseToken().catch(() => {});
}
