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

const fs = require("node:fs");
const path = require("node:path");

/**
 * app.json, plus Firebase for push notifications (ADR-051) when its files are there. The files are never
 * committed: locally they sit next to this file (git ignores them), and EAS builds get them from the file
 * environment variables GOOGLE_SERVICES_JSON and GOOGLE_SERVICE_INFO_PLIST. Without them the app builds as before,
 * and push runs in development mode (src/lib/push.ts).
 */
function firebaseFile(envVar, local) {
  if (process.env[envVar]) return process.env[envVar];
  return fs.existsSync(path.join(__dirname, local)) ? `./${local}` : undefined;
}

/** @param {import("expo/config").ConfigContext} ctx @returns {import("expo/config").ExpoConfig} */
module.exports = ({ config }) => {
  const android = firebaseFile("GOOGLE_SERVICES_JSON", "google-services.json");
  const ios = firebaseFile("GOOGLE_SERVICE_INFO_PLIST", "GoogleService-Info.plist");
  return {
    ...config,
    android: { ...config.android, ...(android && { googleServicesFile: android }) },
    ios: {
      ...config.ios,
      ...(ios && {
        googleServicesFile: ios,
        // push needs the capability; EAS turns it on for the bundle id when it builds
        entitlements: { ...config.ios?.entitlements, "aps-environment": process.env.APS_ENVIRONMENT || "production" },
      }),
    },
    plugins: [
      ...(config.plugins ?? []),
      // React Native Firebase gets the Firebase Apple SDK through Swift Package Manager, which needs dynamic frameworks
      ["expo-build-properties", { ios: { useFrameworks: "dynamic" } }],
      ...(android || ios ? ["@react-native-firebase/app", "@react-native-firebase/messaging"] : []),
    ],
    extra: { ...config.extra, push: { android: Boolean(android), ios: Boolean(ios) } },
  };
};
