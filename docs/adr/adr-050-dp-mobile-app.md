# ADR-050: The DP mobile app: Expo in the frontend workspace, shared rules, PKCE sign-in, secure storage

- **Status:** Accepted
- **Date:** 2026-10-20
- **Implements:** [ADR-032](adr-032-channels-per-audience-web-mobile-console-developer-portal.md) §2, §3 and §4 in part,
  for the DP mobile app

## Context

ADR-032 decided a React Native (Expo) app for principals, with the same capabilities as the web portal, sharing
TypeScript with it, and keeping secrets in the platform's secure storage. This records how the first version does that.

## Decision

1. **Where it lives:** `frontend/apps/dp-mobile`, an Expo SDK 58 app (React Native 0.88, Expo Router) in the
   existing pnpm workspace. Expo supports isolated pnpm installs and configures Metro for the monorepo itself, so no
   hoisting or Metro changes were needed. It uses the workspace's `api-client` and a new `dp-kit` package directly,
   with no copies.
2. **One set of rules for both apps (ADR-032 §3).** `dp-kit` holds a principal's rules as plain functions:
   - status words
   - what may be given and for how long (`choiceProblem`, `giveLabel`)
   - ending soon and renewing
   - shortening
   - how a record's history reads

   The web portal now imports them from there instead of defining them, and `friendlyError` moved to `api-client`.
   Each app keeps only its own rendering: React DOM for the web, React Native for the phone.
3. **Same capabilities as the portal:**
   - **Decide on a request:** none of the data ticked, needed data with what refusing means, validity from what was
     asked up to the purpose's maximum, declining as easy as giving.
   - **Change a consent:** withdraw it, stop sharing one kind of data, end it sooner, or renew it.
   - **History:** each consent's 27560 history, with every signed receipt to save.
   - **Export:** every record at once.
   - **Claiming requests:** requests sent to the person's email before they had an account are claimed on load.
4. **Sign-in:** OAuth 2.0 with PKCE (S256) against the `cm-dp` realm, as a new public client `cm-dp-mobile`.
   - **Sign-in method:** the realm's own passwordless code by email or text, the same as the portal.
   - **Client:** no password grant. Redirects to `cmdp://`, plus `exp://` (Expo Go) and `http://localhost:19006` (the
     web preview), which are for development only.
   - **Refresh token:** kept in the Keychain or Android Keystore, on this device only, never in a backup
     (`WHEN_UNLOCKED_THIS_DEVICE_ONLY`).
   - **Access token:** kept in memory and refreshed 30 seconds before expiry, one refresh at a time.
   - **Signing out** revokes the refresh token and ends Keycloak's session, so the next person on the phone signs in
     as themselves.
   - **An account from another audience** is told so, instead of being shown an empty list.
5. **The web preview** signs in with a full-page redirect rather than a popup, which mobile browsers often block. The
   PKCE verifier and state wait in session storage until the return. The gateway's development CORS allows its
   origin. Native apps send no `Origin`.
6. **Accessibility (ADR-028):**
   - touch targets of at least 48 points, and status always in words
   - checkboxes and pickers exposed with their roles and state
   - the day picker is adjustable by screen readers
   - destructive actions confirmed with the platform's dialog
   - light and dark themes from the system setting

## Not yet

- **Push notifications.** notification-service sends push through FCM only. Android's device token works with it,
  but iOS needs the Firebase SDK in a development build, and both need a Firebase project. They also need
  notification-service's `PushPltf` to accept `ANDROID` and `IOS`. This comes in its own change.
- **Passkeys and app attestation** for high-assurance actions (ADR-032 §4).
- **Offline receipts**, stored encrypted.
- **Notification preferences and organisation pages,** which the portal has.
- **Store builds and a release train** with EAS.

## Consequences

- The web portal and the app can't drift on what may be given or changed: both call the same functions, and both
  test suites run them.
- Expo releases often. The workspace's one-day minimum release age holds back brand-new patches, so `expo-doctor`
  may report patch versions a day behind. That is intended.
- The `cm-dp` realm now has two clients. The theme test allows each audience's own apps and still requires both to
  be public, use PKCE S256, and use the audience's theme.
