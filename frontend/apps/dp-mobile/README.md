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
| `src/lib/` | Sign-in and tokens, the consents, sharing files, settings |

Push notifications aren't in this version; see ADR-050, "Not yet".
