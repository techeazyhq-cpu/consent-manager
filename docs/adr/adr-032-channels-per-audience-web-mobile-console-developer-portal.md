# ADR-032: Channels per audience: DP web and mobile, DF Console, developer portal

- **Status:** Proposed; extends [ADR-009](adr-009-independent-micro-frontends.md)
- **Date:** 2026-10-11
- **Design:** [product architecture §1-§5](../product/product-architecture.md)

## Context

ADR-009 set out three independent web apps: DP portal, DF app and CM Admin. The product now needs more channels:

- **A mobile app for principals.** DPDPA First Schedule B5 makes a website or app the primary means of access, and many
  principals reach services by phone first.
- **A developer portal with a sandbox and an API playground,** so fiduciaries can integrate without touching
  production.

The team's stack is React and TypeScript, with shared `api-client`, `auth-kit` and `ui-kit` packages.
notification-service already delivers push through FCM, which covers Android and, through APNs, iOS.

## Decision

1. **Independent apps, one per audience**, each with its own build, release and sign-in. Code is shared at source,
   never at runtime (ADR-009):

   | App | Status |
   |---|---|
   | `mf-dp-portal` (web) | Existing |
   | **DP mobile app** (iOS and Android) | New |
   | `mf-df-console` (web) | Replaces `mf-df-app` |
   | **`mf-dev-portal`** (web: docs, sandbox, playground) | New |
   | `mf-cm-admin` (web) | Existing |
2. **The DP mobile app is built with React Native (Expo),** sharing TypeScript, the generated `api-client`, `auth-kit`
   logic and the design tokens. Platform-specific code is kept to secure storage, passkeys, push and app attestation.
3. **The web and mobile DP apps have the same capabilities.** The consent decision is one component specification
   (ADR-028), implemented for web and for native, and tested against the same rules.
4. **Mobile security and privacy:**
   - push carries titles only; the app fetches details after the tap
   - secrets live in the platform's secure storage (Keychain, Android Keystore); offline receipts are stored encrypted
   - app attestation (App Attest, Play Integrity) is required for high-assurance actions such as HIPAA signing
   - one app serves every region, and talks to one cell per account (ADR-018)
5. **Push goes through notification-service's FCM provider** (in line with ADR-010), with device tokens registered as
   today's web push devices are.

## Options considered

- **Native Kotlin and Swift:** best platform fidelity, but two more codebases and languages, and the decision
  component implemented three times.
- **Flutter:** one codebase, but a new language and no sharing with the existing TypeScript packages.
- **A progressive web app only:** no app-store presence, a weaker push story on iOS, and no platform attestation for
  signing.

## Consequences

- Principals get the same rights and journeys wherever they are. Fiduciaries get a place to learn and test that can't
  harm anyone.
- Two more apps to release and support, including app-store review, plus a mobile release train.
- The `api-client` becomes generated from the OpenAPI contracts (ADR-030), so all apps stay in step.

## Compliance impact

- DPDPA s.2(g) (accessible platform) and First Schedule B5.
- WCAG 2.2 and the mobile platforms' accessibility services (ADR-028).
- HIPAA signing integrity (ADR-024).
- Residency per account (ADR-018).
