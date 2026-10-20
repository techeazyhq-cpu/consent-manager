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

import { PermissionsAndroid, Platform } from "react-native";
import type { Messaging } from "@react-native-firebase/messaging";
import type { Permission, PushMessage, Unsubscribe } from "./fcm";

export type { Permission, PushMessage, Unsubscribe } from "./fcm";

/**
 * FCM on the phones, through React Native Firebase. It gives an FCM token on Android and on iOS alike (there it
 * exchanges the APNs token for one), which is what notification-service's FCM provider pushes to (ADR-051).
 *
 * The module is loaded on first use, never at import: Expo Go and builds without the Firebase files have no
 * Firebase native code, and push.ts keeps those in development mode, so they never get here.
 */
type Fcm = typeof import("@react-native-firebase/messaging");

let loaded: { fcm: Fcm; messaging: Messaging } | null = null;

function load() {
  if (!loaded) {
    // eslint-disable-next-line @typescript-eslint/no-require-imports
    const fcm: Fcm = require("@react-native-firebase/messaging");
    loaded = { fcm, messaging: fcm.getMessaging() };
  }
  return loaded;
}

// Android 13 and later ask for POST_NOTIFICATIONS at run time; earlier versions allow notifications from install.
const needsAndroidPrompt = () => Platform.OS === "android" && Number(Platform.Version) >= 33;

export async function permission(): Promise<Permission> {
  if (Platform.OS === "android") {
    if (!needsAndroidPrompt()) return "granted";
    return (await PermissionsAndroid.check(PermissionsAndroid.PERMISSIONS.POST_NOTIFICATIONS)) ? "granted" : "undetermined";
  }
  const { fcm, messaging } = load();
  const status = await fcm.hasPermission(messaging);
  if (status === fcm.AuthorizationStatus.NOT_DETERMINED) return "undetermined";
  return status === fcm.AuthorizationStatus.DENIED ? "denied" : "granted";
}

export async function askPermission(): Promise<Permission> {
  if (Platform.OS === "android") {
    if (!needsAndroidPrompt()) return "granted";
    const result = await PermissionsAndroid.request(PermissionsAndroid.PERMISSIONS.POST_NOTIFICATIONS);
    return result === PermissionsAndroid.RESULTS.GRANTED ? "granted" : "denied";
  }
  // Deprecated in React Native Firebase 26 in favour of a permissions library; it still works, and adding
  // expo-notifications instead would put a second FCM service and notification delegate next to Firebase's.
  const { fcm, messaging } = load();
  const status = await fcm.requestPermission(messaging, { alert: true, badge: false, sound: true });
  return status === fcm.AuthorizationStatus.AUTHORIZED || status === fcm.AuthorizationStatus.PROVISIONAL ? "granted" : "denied";
}

export async function getFcmToken(): Promise<string> {
  const { fcm, messaging } = load();
  if (Platform.OS === "ios" && !fcm.isDeviceRegisteredForRemoteMessages(messaging)) {
    await fcm.registerDeviceForRemoteMessages(messaging);
  }
  return fcm.getToken(messaging);
}

/** Stops FCM delivering to this install; the device row is removed separately through the API. */
export async function deleteFcmToken(): Promise<void> {
  const { fcm, messaging } = load();
  await fcm.deleteToken(messaging);
}

const asMessage = (m: { data?: Record<string, unknown> } | null): PushMessage | null => (m ? { data: m.data } : null);

/** The notification whose tap started the app from closed, once. */
export async function initialTap(): Promise<PushMessage | null> {
  const { fcm, messaging } = load();
  return asMessage(await fcm.getInitialNotification(messaging));
}

/** A tap that brings the app back from the background. */
export function onTap(listener: (message: PushMessage) => void): Unsubscribe {
  const { fcm, messaging } = load();
  return fcm.onNotificationOpenedApp(messaging, (m) => listener(asMessage(m)!));
}

/** A push that arrives while the app is open; the system shows no notification then. */
export function onForeground(listener: (message: PushMessage) => void): Unsubscribe {
  const { fcm, messaging } = load();
  return fcm.onMessage(messaging, (m) => listener(asMessage(m)!));
}

export function onTokenChange(listener: (token: string) => void): Unsubscribe {
  const { fcm, messaging } = load();
  return fcm.onTokenRefresh(messaging, listener);
}
