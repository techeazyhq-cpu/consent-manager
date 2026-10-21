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
 * Where the app signs in and what it calls, set per build with EXPO_PUBLIC_ variables (inlined at build time, so
 * nothing secret belongs here). The defaults are the local stack; a phone running Expo Go needs the computer's LAN
 * address instead of localhost, for example EXPO_PUBLIC_GW_BASE_URL=http://192.168.1.20:9000.
 */
export const KC_BASE = process.env.EXPO_PUBLIC_KC_BASE_URL ?? "http://localhost:8080";
export const KC_REALM = process.env.EXPO_PUBLIC_KC_REALM ?? "cm-dp";
export const KC_CLIENT_ID = process.env.EXPO_PUBLIC_KC_CLIENT_ID ?? "cm-dp-mobile";
export const GW_BASE = process.env.EXPO_PUBLIC_GW_BASE_URL ?? "http://localhost:9000";

export const ISSUER = `${KC_BASE}/realms/${KC_REALM}`;

/** notification-service's catcher, which the dev push relay polls in development mode only (ADR-051). */
export const PUSH_CATCHER = process.env.EXPO_PUBLIC_PUSH_CATCHER_URL ?? "http://localhost:19000";

/** The deep-link scheme in app.json: Keycloak returns here after sign-in and sign-out. */
export const SCHEME = "cmdp";
