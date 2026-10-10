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

notification-service runs as its own compose project from a sibling checkout (`../notification-service`).
[compose.cm-ports.yml](compose.cm-ports.yml) moves all its ports up by 10000 so it doesn't clash with this stack:
client API `18080`, admin API `18081`, Mailpit `18025`, SMS/WhatsApp/push catcher `19000`, admin UI `15173`,
client UI `15174`.

```bash
# 1. notification-service (from this repo's root)
docker compose -f ../notification-service/docker-compose.yml -f infra/notification-service/compose.cm-ports.yml up -d --build client-api admin-api dispatcher mailpit catcher

# 2. its providers and templates
(cd ../notification-service && ADMIN_URL=http://localhost:18081 node scripts/seed.mjs)

# 3. register consent-manager as a client; writes cm-client.env (git-ignored) with the API key
node infra/notification-service/register-cm-client.mjs

# 4. this stack: cm-ntf-svc loads cm-client.env and switches to notification-service
./gradlew bootJar
docker compose -f infra/docker-compose.yml up -d --build

# 5. end-to-end check, reading email from notification-service's Mailpit
MAILPIT_URL=http://localhost:18025 scripts/verify-phase1.sh
```

SMS, WhatsApp and push then land in notification-service's catcher (http://localhost:19000), and every message is
visible in its client UI (http://localhost:15174 with the key from `cm-client.env`, if you also start `client-ui`).
Delete `cm-client.env` and recreate `cm-ntf-svc` to go back to the local catchers.
