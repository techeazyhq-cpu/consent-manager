# Delivering through notification-service

`cm-ntf-svc` can deliver email, SMS, WhatsApp and push through
[notification-service](https://github.com/techeazyhq-cpu/notification-service) instead of its local Mailpit and
in-memory catchers. `cm.ntf.provider` chooses:

| `cm.ntf.provider` | Email | SMS / WhatsApp / push |
|---|---|---|
| `local` (default) | Mailpit (`:8025`) | in-memory catchers: `GET :8084/dev/{sms,whatsapp,push}-catcher` |
| `notification-service` | notification-service's client API (`POST /v1/notifications`) | same |

With `notification-service`, each message carries its `ref` as `Idempotency-Key` and `clientReference`
(`cm-notify-dp-<cnsReqId>`, `cm-otp-<uuid>`), so a redelivered Pulsar command never sends twice and every message is
searchable in notification-service's client console. OTPs go as `category: OTP` (priority, never sent after
`otp-validity-seconds`). 429/5xx/timeouts are retried with the same key; any other refusal is final: the Pulsar
consumer acknowledges it with a warning, and `POST /internal/otp/send` answers `422 {"code": ...}`.

## Settings (`cm-ntf-boot/src/main/resources/application.yml`)

| Env var | Property | Default |
|---|---|---|
| `CM_NTF_PROVIDER` | `cm.ntf.provider` | `local` |
| `NTF_SVC_BASE_URL` | `cm.ntf.notification-service.base-url` | `http://localhost:18080` (docker profile: `http://host.docker.internal:18080`) |
| `NTF_SVC_API_KEY` | `cm.ntf.notification-service.api-key` | required with `notification-service` |
| `NTF_SVC_SIGNING_SECRET` | `cm.ntf.notification-service.signing-secret` | empty = unsigned requests |
| `NTF_SVC_FROM_ADDRESS` | `cm.ntf.notification-service.from-address` | empty = notification-service's default sender |

## Running both stacks locally

Both run on the servers in the `common` project (ADR-053): one Postgres, Redis, Pulsar, Keycloak and Mailpit. Each
app has its own database, Redis index and Pulsar tenant. notification-service runs in its shared mode
(`docker-compose.shared.yml` in its repository) from a sibling checkout (`../notification-service`), as its own project
named `notification`. Its ports are the client API `18080`, the admin API `18081`, the SMS/WhatsApp/push catcher
`19000`, and the admin and client UIs `15173` and `15174`. Email from both apps lands in one inbox:
http://localhost:8025.

```bash
# 1. the shared servers (from this repo's root), and wait until they're healthy
docker compose -f infra/common/compose.yml up -d --wait

# 2. notification-service, in its shared mode
(cd ../notification-service && docker compose -f docker-compose.yml -f docker-compose.shared.yml --profile app up -d --build)

# 3. its providers and templates (once)
(cd ../notification-service && ADMIN_URL=http://localhost:18081 node scripts/seed.mjs)

# 4. register consent-manager as a client (once); writes cm-client.env (git-ignored) with the API key
node infra/notification-service/register-cm-client.mjs

# 5. this stack: cm-ntf-svc loads cm-client.env and switches to notification-service
./gradlew bootJar
docker compose -f infra/docker-compose.yml up -d --build

# 6. end-to-end check
scripts/verify-phase1.sh
```

SMS, WhatsApp and push land in notification-service's catcher (http://localhost:19000). Every message is visible in
its client UI (http://localhost:15174, with the key from `cm-client.env`). Delete `cm-client.env` and recreate
`cm-ntf-svc` to go back to the local catchers.

**Stopping and starting.** Stop the apps before `common`, and start `common` first. `docker compose down` on `common`
removes the `common` network, and containers created before that still point at the old one; they fail with
`UnknownHostException: postgres`. If that happens, recreate them with `up -d --force-recreate`. `down` without `-v`
keeps every volume. Pulsar gets 60 seconds to stop: a kill after compose's default 10 seconds corrupted its ledger.

## Web push from the DP portal

A DP turns on **Notify me** in the DP portal; the browser's push token is stored per DP in `cm-ntf-svc`
(`ntf.push_dvc`, via `POST/GET/DELETE /api/ntf/push-dvcs`, the DP taken from the JWT). When a DF raises a consent
request for a linked DP, `cm-ntf-svc` pushes "New consent request" with the notice title to each of that DP's
browsers (`clientReference` `cm-notify-dp-<cnsReqId>-<deviceId>`). Signing out of the portal removes that browser.

The portal gets real Firebase Cloud Messaging tokens when built with a Firebase web app's config:

| Env var (`frontend/packages/mf-dp-portal`) | From the Firebase console |
|---|---|
| `VITE_FIREBASE_API_KEY`, `VITE_FIREBASE_PROJECT_ID`, `VITE_FIREBASE_MESSAGING_SENDER_ID`, `VITE_FIREBASE_APP_ID` | Project settings → Your apps → Web app config |
| `VITE_FIREBASE_VAPID_KEY` | Project settings → Cloud Messaging → Web Push certificates |

Without them, `pnpm dev` registers a stand-in `dev-web-…` token so the flow can be tried end to end against the
catchers, and a production build hides the toggle. Delivering to real browsers also needs notification-service to
have an FCM provider for the `PUSH` channel; its generic `HTTP_JSON` provider only reaches the local catcher.
