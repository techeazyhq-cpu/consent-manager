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

import { Platform } from "react-native";
import Constants, { ExecutionEnvironment } from "expo-constants";
import * as Crypto from "expo-crypto";
import * as SecureStore from "expo-secure-store";
import { ApiError, ntf } from "api-client";
import { GW_BASE } from "./config";
import * as fcm from "./fcm";
import { devToken, pushModeFor, pushPlatform, stillRegistered } from "./push";

/** This phone's push registration with cm-ntf-svc, as the portal's push.ts and PushToggle do it for a browser. */
export const pushMode = pushModeFor({
  os: Platform.OS,
  firebase: (Constants.expoConfig?.extra?.push ?? {}) as { android?: boolean; ios?: boolean },
  expoGo: Constants.executionEnvironment === ExecutionEnvironment.StoreClient,
  dev: __DEV__,
});

const platform = pushPlatform(Platform.OS);

/** The person said no to notifications; only they can undo that, in the phone's settings. */
export class PushBlockedError extends Error {
  constructor() {
    super("Notifications are turned off for this app in your phone's settings.");
  }
}

const DEVICE_ID_KEY = "cm.push.dvcId";
const DEV_TOKEN_KEY = "cm.push.devToken";

// Which registered device this phone is. Not a secret, but SecureStore is already here; the web preview uses
// localStorage, and starts "off" on each visit if storage is unavailable.
const kv = {
  get: async (key: string): Promise<string | null> => {
    if (Platform.OS !== "web") return SecureStore.getItemAsync(key);
    try {
      return localStorage.getItem(key);
    } catch {
      return null;
    }
  },
  set: async (key: string, value: string | null): Promise<void> => {
    if (Platform.OS !== "web") {
      await (value === null ? SecureStore.deleteItemAsync(key) : SecureStore.setItemAsync(key, value));
      return;
    }
    try {
      if (value === null) localStorage.removeItem(key);
      else localStorage.setItem(key, value);
    } catch {
      // see get
    }
  },
};

export async function notificationsBlocked(): Promise<boolean> {
  return pushMode === "fcm" && (await fcm.permission()) === "denied";
}

/** Asks for permission if need be, and returns this phone's push token. */
async function obtainToken(): Promise<string> {
  if (pushMode === "dev") {
    let token = await kv.get(DEV_TOKEN_KEY);
    if (!token) {
      token = devToken(platform, Crypto.randomUUID());
      await kv.set(DEV_TOKEN_KEY, token);
    }
    return token;
  }
  if ((await fcm.askPermission()) !== "granted") throw new PushBlockedError();
  return fcm.getFcmToken();
}

/** Turns notifications on for this phone. */
export async function registerThisPhone(accessToken: string): Promise<void> {
  const dvc = await ntf.registerPushDvc(GW_BASE, accessToken, { token: await obtainToken(), platform });
  await kv.set(DEVICE_ID_KEY, dvc.id);
}

/**
 * Whether notifications are on for this phone. While they are, the token is registered again, since FCM tokens
 * rotate and the server's copy must stay current.
 */
export async function checkThisPhone(accessToken: string): Promise<"on" | "off" | "blocked"> {
  const devices = await ntf.listPushDvcs(GW_BASE, accessToken);
  if (!stillRegistered(await kv.get(DEVICE_ID_KEY), devices)) {
    await kv.set(DEVICE_ID_KEY, null);
    return (await notificationsBlocked()) ? "blocked" : "off";
  }
  if (pushMode === "dev" || (await fcm.permission()) === "granted") await registerThisPhone(accessToken);
  return "on";
}

/** This phone's stand-in token while development-mode notifications are on for it, for the dev push relay. */
export async function devTokenIfOn(): Promise<string | null> {
  if (pushMode !== "dev" || !(await kv.get(DEVICE_ID_KEY))) return null;
  return kv.get(DEV_TOKEN_KEY);
}

/** Re-registers after FCM rotates the token, if notifications are on for this phone. */
export async function tokenChanged(accessToken: string): Promise<void> {
  if (await kv.get(DEVICE_ID_KEY)) await registerThisPhone(accessToken);
}

/**
 * Removes this phone's device, when the person turns notifications off or signs out, so a shared phone stops
 * getting their notifications. A device already gone (404) counts as removed.
 */
export async function forgetThisPhone(accessToken: string): Promise<void> {
  const id = await kv.get(DEVICE_ID_KEY);
  if (id === null) return;
  try {
    await ntf.removePushDvc(GW_BASE, accessToken, id);
  } catch (err) {
    if (!(err instanceof ApiError && err.status === 404)) throw err;
  }
  await kv.set(DEVICE_ID_KEY, null);
  if (pushMode === "fcm") await fcm.deleteFcmToken().catch(() => undefined);
}
