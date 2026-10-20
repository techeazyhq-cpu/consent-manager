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

import type { PushDvcRespDto, PushPltf } from "api-client";

/**
 * The app's push rules, kept free of React Native so the workspace's tests run them. notification-service pushes
 * through Firebase Cloud Messaging only (ADR-032 §5), so a phone always registers an FCM token, on iOS too.
 *
 * - `fcm`: a build made with the Firebase files for this platform (app.config.js) gets a real FCM token.
 * - `dev`: Expo Go, the web preview, or a development build without Firebase registers a stand-in token, so
 *   register → notify → cm-ntf-svc's push catcher still works locally. Nothing reaches the phone.
 * - `off`: a release build without Firebase hides the feature.
 */
export type PushMode = "fcm" | "dev" | "off";

export interface PushEnv {
  /** Platform.OS */
  os: string;
  /** Which platforms this build has Firebase files for (app.config.js puts it in `extra.push`). */
  firebase: { android?: boolean; ios?: boolean };
  /** Running inside Expo Go, which has no Firebase native module. */
  expoGo: boolean;
  /** __DEV__ */
  dev: boolean;
}

export function pushModeFor({ os, firebase, expoGo, dev }: PushEnv): PushMode {
  const configured = (os === "android" && firebase.android) || (os === "ios" && firebase.ios);
  if (configured && !expoGo) return "fcm";
  return dev ? "dev" : "off";
}

/** What the device list calls this phone. The web preview is a browser, as the portal is. */
export function pushPlatform(os: string): PushPltf {
  return os === "android" ? "ANDROID" : os === "ios" ? "IOS" : "WEB";
}

/** The stand-in token of development mode: unique per install, and plainly not an FCM token. */
export function devToken(platform: PushPltf, uuid: string): string {
  return `dev-${platform.toLowerCase()}-${uuid}`;
}

/** Whether the device this phone remembers registering is still among the person's devices. */
export function stillRegistered(storedId: string | null, devices: Pick<PushDvcRespDto, "id">[]): boolean {
  return storedId !== null && devices.some((d) => d.id === storedId);
}

const CNS_REQ_ID = /^[A-Za-z0-9_-]{1,64}$/;

/**
 * The consent request a tapped notification is about, if it names one. The push carries no details (ADR-032 §4):
 * the app fetches them after the tap. Today notification-service forwards only its own `messageId` in the FCM data,
 * so a tap opens the list of requests; once it forwards a `cnsReqId`, the tap opens that request's screen
 * (cmdp://consent/<cnsReqId>). Anything that doesn't look like an id is ignored, since the data is outside input.
 */
export function cnsReqIdOf(data: Record<string, unknown> | null | undefined): string | null {
  const id = data?.cnsReqId;
  return typeof id === "string" && CNS_REQ_ID.test(id) ? id : null;
}
