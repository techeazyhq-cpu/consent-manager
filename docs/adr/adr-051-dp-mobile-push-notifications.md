# ADR-051: Push notifications in the DP mobile app: FCM tokens on both platforms, through notification-service

- **Status:** Accepted
- **Date:** 2026-10-20
- **Implements:** [ADR-032](adr-032-channels-per-audience-web-mobile-console-developer-portal.md) §4 (push carries
  titles only) and §5 (push through notification-service's FCM provider) for the
  [DP mobile app (ADR-050)](adr-050-dp-mobile-app.md)

## Context

A principal should hear about a consent request when an organisation sends one, not only the next time they open the
app. The web portal already does this: it registers the browser's FCM token as a push device, and cm-ntf-svc pushes a
title to every device of a linked principal through the user's notification-service.

Two things shape the mobile version:
- notification-service has an FCM provider and no APNs provider, so every device must have an **FCM** token, on iOS
  too
- cm-ntf-svc never calls FCM itself, and nothing goes through Expo's push service

## Decision

1. **The path is the web portal's.**
   - The app gets an FCM registration token and registers it with `POST /api/ntf/push-dvcs` as
     `{ token, platform }`.
   - When a linked principal gets a request, cm-ntf-svc sends one push per device to notification-service, which
     delivers it through FCM (and FCM through APNs on iOS).
2. **`PushPltf` is now `WEB`, `ANDROID` or `IOS`.**
   - All three are FCM tokens. The platform only tells the principal which of their devices is which.
   - A registration that names no platform is still a browser.
   - `ntf.push_dvc.platform` is an unconstrained `varchar(16)`, so no migration was needed.
3. **React Native Firebase (`@react-native-firebase/messaging`) on both platforms.**
   - On iOS the Firebase SDK exchanges the APNs token for an FCM token, which is the only kind notification-service
     can push to.
   - Using it on Android too gives one token source and one tap handler.
   - expo-notifications was not added. It brings its own `FirebaseMessagingService` on Android and its own
     notification delegate on iOS, which would compete with Firebase's for the same messages and taps.
   - Permission is asked with React Native's `PermissionsAndroid` (Android 13+) and Firebase's `requestPermission` on
     iOS. The latter is deprecated in React Native Firebase 26 but still supported; replace it when a permissions
     library is added for other reasons.
   - It needs a development build. Expo Go has no Firebase native code.
4. **Opt in, per phone, on the Account screen ("Notify me"),** as the portal's toggle.
   - While notifications are on, the token is registered again each time the screen opens and whenever FCM rotates
     it.
   - Turning them off, or signing out, removes the device and deletes the FCM token. A shared phone then stops
     getting the previous person's notifications. A token registered by someone else moves to them (existing
     behaviour).
5. **Titles only (ADR-032 §4).**
   - The push says "New consent request" with the notice's title.
   - The notice text and the data asked for are fetched by the app after the tap.
   - Tapping opens the request named by `data.cnsReqId` (`cmdp://consent/<cnsReqId>`), or the list of requests when
     the push names none. Either way, the list is fetched again first.
   - cm-ntf-svc sends `data: {cnsReqId}` with each push, and notification-service puts it in the FCM data next to
     its own `messageId` (see Consequences). The request id is opaque, so the push still carries a title only.
   - A push that arrives while the app is open refreshes the list. The system shows no notification then.
6. **Three modes, as the portal has:**
   - **`fcm`:** a phone build made with that platform's Firebase file.
   - **`dev`:** Expo Go, the web preview, or a development build without Firebase. It registers a stand-in token
     `dev-<platform>-<uuid>`, so register, notify and catch still work locally. The push lands in
     notification-service's catcher, or cm-ntf-svc's push catcher with `CM_NTF_PROVIDER=local`. Nothing reaches the
     phone by the platform's push service.
     - **The dev push relay** fills that gap, so the project can be tried end to end with no Firebase account. While
       the app is open, it polls the catcher's `GET /messages?channel=push&to=<token>&since=…` every 3 seconds.
       Each new push appears as an in-app banner, and tapping it runs the same code as tapping a real notification.
     - It is a banner, not a system notification: one would need expo-notifications (see 3), and polling stops in
       the background anyway.
     - It exists only in `__DEV__` builds, and reads the catcher at `EXPO_PUBLIC_PUSH_CATCHER_URL`.
     - The catcher allows cross-origin reads only (notification-service PR #147), so the web preview can poll it.
     - Firebase has no local emulator for Cloud Messaging. On the iOS Simulator, `xcrun simctl push` shows a real
       system notification without APNs, for checking how it looks.
   - **`off`:** a release build without Firebase. The toggle isn't shown.
7. **Firebase files are configuration, never committed.**
   - `app.config.js` reads `google-services.json` and `GoogleService-Info.plist` from the app folder (git ignores
     them), or from the EAS file environment variables `GOOGLE_SERVICES_JSON` and `GOOGLE_SERVICE_INFO_PLIST`.
   - Only when a file is there does it add the Firebase config plugins, the platform's `googleServicesFile`, the iOS
     `aps-environment` entitlement, and `extra.push` (which the app reads for its mode).
   - `expo-build-properties` sets `ios.useFrameworks: "dynamic"` always, because React Native Firebase's iOS SDK
     comes through Swift Package Manager, which needs it.
   - The APNs authentication key is uploaded to the Firebase project, not to this repository.

## Options considered

- **expo-notifications' device token on Android, React Native Firebase on iOS:** two token paths and two tap
  handlers, and the two libraries' Android services clash when both are linked.
- **An APNs provider in notification-service:** iOS tokens would go to APNs directly, without the Firebase SDK. It
  is the cleaner end state for iOS, but it is a change to notification-service and would mean a second
  credential to manage. It stays open.
- **Expo's push service:** one token for both platforms, but pushes would leave through a third party outside
  notification-service, against ADR-010 and ADR-032 §5.
- **FCM straight from cm-ntf-svc:** duplicates notification-service's provider, rate limits and retries.

## Consequences

- Push works only in a development or store build that has the Firebase files. Expo Go and the web preview stay in
  development mode.
- iOS builds use dynamic frameworks, which slightly lengthens app start-up.
- *Updated 2026-10-20:* tapping a notification opens the request's own screen. notification-service accepts a
  small `data` map on a push (ADR-038 there) and merges it into the FCM data next to `messageId`, which it can't
  override; cm-ntf-svc sends `{cnsReqId}` (`cm.ntf.notification-service.push-data`, on by default; an older
  notification-service ignores the field, so taps there open the list as before). The local push catcher shows the
  data too, so `GET /dev/push-catcher` on cm-ntf-svc proves what a tap would open.
- Native delivery and taps can only be checked on a real device with the Firebase project set up. Locally,
  registration and delivery to the catcher are checked end to end with stand-in tokens.

## Compliance impact

- DPDPA s.5–6 and First Schedule B5: a principal learns of a request promptly, on the device they choose, and can
  stop it at any time.
- Data minimisation: the push travels through Google's and Apple's services with a title only. No notice text and no
  data categories are included, and the token is never returned by the API.
