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

/**
 * Firebase Cloud Messaging, as the rest of the app sees it. This file is the web preview's: it has no FCM, so push
 * there only ever runs in development mode (see push.ts). fcm.native.ts is the phones'.
 */
export type Permission = "granted" | "denied" | "undetermined";

export interface PushMessage {
  data?: Record<string, unknown>;
}

export type Unsubscribe = () => void;

export const permission = async (): Promise<Permission> => "denied";
export const askPermission = async (): Promise<Permission> => "denied";
export const getFcmToken = async (): Promise<string> => {
  throw new Error("Notifications need the app on a phone");
};
export const deleteFcmToken = async (): Promise<void> => undefined;
export const initialTap = async (): Promise<PushMessage | null> => null;
export const onTap = (_listener: (message: PushMessage) => void): Unsubscribe => () => undefined;
export const onForeground = (_listener: (message: PushMessage) => void): Unsubscribe => () => undefined;
export const onTokenChange = (_listener: (token: string) => void): Unsubscribe => () => undefined;
