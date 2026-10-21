# Consent Manager: the app for data principals

The iOS and Android app for people whose consent organisations ask for (ADR-032, ADR-050). It does what the web
portal does:
- decide on requests: what to share, and for how long
- withdraw consent, stop sharing one kind of data, end it sooner, or renew it
- keep the signed record of every decision

Expo SDK 58, Expo Router, in the frontend pnpm workspace. It uses `api-client` and `dp-kit` (the rules shared with
the portal) directly.

## Run it

From `frontend/`, with the local stack up (`docker compose -f infra/docker-compose.yml up -d` from the repository root):

```bash
pnpm install
```

```bash
pnpm --dir apps/dp-mobile exec expo start --web --port 19006
```

That's the web preview at http://localhost:19006. Sign in as `dp-demo@cm.local`; the code arrives in Mailpit at
http://localhost:18025.

**On a phone with Expo Go:** run `pnpm --dir apps/dp-mobile start` and scan the QR code. The phone can't reach
`localhost`, so point the app at your computer's address first:

```bash
EXPO_PUBLIC_GW_BASE_URL=http://192.168.1.20:9000 EXPO_PUBLIC_KC_BASE_URL=http://192.168.1.20:8080 pnpm --dir apps/dp-mobile start
```

Keycloak must also be reachable at that address for the sign-in page.

## Settings

Each is an `EXPO_PUBLIC_` variable, built into the app; none is secret.

| Variable | Default |
|---|---|
| `EXPO_PUBLIC_GW_BASE_URL` | `http://localhost:9000` |
| `EXPO_PUBLIC_KC_BASE_URL` | `http://localhost:8080` |
| `EXPO_PUBLIC_KC_REALM` | `cm-dp` |
| `EXPO_PUBLIC_KC_CLIENT_ID` | `cm-dp-mobile` |

## Push notifications

When an organisation sends a consent request, the person can be told on their phone (ADR-051). It is off until
they turn it on under **Account → Notifications**. Signing out turns it off for that phone.

- The push says only "New consent request" and the request's title. The app fetches the rest after the tap.
- Pushes go through notification-service's FCM provider, on iOS too, so the app needs an FCM token from React
  Native Firebase on both platforms.

**Development mode.** Expo Go, the web preview, and builds without Firebase register a stand-in token
(`dev-android-…`, `dev-ios-…`, `dev-web-…`) instead. Turn notifications on, send a request to that person, and the
push appears in the push catcher:
- notification-service's catcher at http://localhost:19000 (the stack's default `CM_NTF_PROVIDER=notification-service`)
- or cm-ntf-svc's `/dev/push-catcher` with `CM_NTF_PROVIDER=local`

Nothing reaches the phone.

**Real push on a phone** needs a Firebase project and a development build. Expo Go can't do it.
1. In the Firebase console, add an Android app (`com.techeazyhq.cm.dp`) and an iOS app (same bundle id). Download
   `google-services.json` and `GoogleService-Info.plist`.
2. For iOS, upload an APNs authentication key (`.p8`) under Project settings → Cloud Messaging.
3. Give notification-service's FCM provider a service account of the same project.
4. Locally, put both files in this folder. Git ignores them; never commit them. For EAS, store them as file
   environment variables instead:

   ```bash
   npx eas-cli@latest env:create --name GOOGLE_SERVICES_JSON --type file --value ./google-services.json --visibility secret
   ```

   ```bash
   npx eas-cli@latest env:create --name GOOGLE_SERVICE_INFO_PLIST --type file --value ./GoogleService-Info.plist --visibility secret
   ```

5. Build: `eas build --profile development`, or `npx expo run:android` with an Android SDK.

`app.config.js` adds the Firebase plugins only when a file is present, so builds without them work as before. iOS
builds always use dynamic frameworks, which React Native Firebase needs. `APS_ENVIRONMENT` (default `production`)
sets the iOS push entitlement.

**Tapping a notification** opens that request's screen (`cmdp://consent/<cnsReqId>`): cm-ntf-svc sends the request
id as `data.cnsReqId` and notification-service forwards it in the FCM data. A push without one (from an older
notification-service) opens the list of requests.

## Checks

```bash
pnpm --dir apps/dp-mobile typecheck
```

The plain-TypeScript tests (`src/lib/*.test.ts`) run with the workspace's `pnpm test`.

## Layout

| Path | What |
|---|---|
| `src/app/` | Screens (Expo Router): home, a consent, its history, account, the sign-in return |
| `src/components/` | Deciding, changing a consent, and the accessible building blocks |
| `src/lib/` | Sign-in and tokens, the consents, push (`push.ts` rules, `pushDevice.ts` registration, `fcm*.ts` Firebase), sharing files, settings |
| `app.config.js` | app.json plus Firebase, when its files are there |
