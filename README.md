# consent-manager

A **Consent Manager** platform in the sense of India's Digital Personal Data Protection Act (DPDPA): Data Fiduciaries
(organisations) ask Data Principals (people) for consent to process their personal data for a stated purpose, and the
person gives, refuses or withdraws it in one place. Every decision becomes a signed **consent artefact** that a
fiduciary's systems check before they touch the data.

- **Data Principals** use the **DP portal** to see every organisation they have dealt with, and approve, deny or
  revoke consent. They are notified by email, SMS or web push, and sign in with a one-time code; no password.
- **Data Fiduciaries** use the **DF app** and the **DF integration API** to raise consent requests and verify a
  consent token before processing.
- **Platform administrators** use **CM Admin** to onboard and activate fiduciaries and manage their subscriptions.

Built with Spring Boot WebFlux and R2DBC, Apache Pulsar, PostgreSQL, Keycloak, Open Policy Agent and React.
Notifications are delivered through [notification-service](https://github.com/techeazyhq-cpu/notification-service).

## Architecture

| Service | Port | Responsibility |
|---|---|---|
| `cm-gw` | 9000 | API gateway: routing, JWT validation, policy enforcement through OPA (PEP/PDP), per-fiduciary rate limits |
| `cm-df-reg-svc` | 8081 | Fiduciary registration and activation; provisions each fiduciary's API client in Keycloak |
| `cm-cns-cmd-svc` | 8082 | Consent lifecycle (write side): requests, approval, denial, revocation, expiry; signs consent tokens; the lifecycle saga |
| `cm-cns-qry-svc` | 8083 | Consent read side: projections of the lifecycle events, the DP dashboard, the status check behind token verification |
| `cm-ntf-svc` | 8084 | Notifications to data principals and fiduciaries, one-time codes, DP web-push devices |
| `cm-bil-svc` | 8085 | Subscription plans, usage metering, eligibility checks |
| `cm-aud-svc` | 8086 | Tamper-evident audit ledger: every platform event in a SHA-256 hash chain, with Merkle checkpoints timestamped by an external RFC 3161 authority ([how to verify](docs/audit-ledger.md)) |
| `cm-idn-otp-spi` | (Keycloak) | Keycloak provider: passwordless sign-in with an email or SMS code |
| `cm-idn-theme` | (Keycloak) | Keycloak login themes: one sign-in page per audience (data principal, fiduciary, admin) |

Each bounded context owns its schema and talks to the others through Pulsar events published from a transactional
outbox. Writes and reads are separate services (CQRS), and the consent lifecycle across services is coordinated by a
saga. Inside each service, the code is split into domain, application, infrastructure and interface modules, and an
ArchUnit test keeps the dependencies pointing inwards. The decisions are recorded in [docs/adr](docs/adr).

| Frontend | Port | Users |
|---|---|---|
| `mf-dp-portal` | 5174 | Data principals |
| `mf-df-app` | 5175 | Data fiduciaries |
| `mf-cm-admin` | 5176 | Platform administrators |

The three frontends are independent apps (no shared shell); they share the `auth-kit`, `api-client` and `ui-kit`
packages at source level.

## Run it

Requires JDK 21, Docker, Node 20+ and pnpm.

```bash
./gradlew build                                       # services, Keycloak providers, tests
docker compose -f infra/docker-compose.yml up -d --build
bash infra/pulsar/init-tenant-namespaces.sh           # once, after Pulsar first starts
cd frontend && pnpm install && pnpm dev:dp-portal     # or dev:df-app, dev:cm-admin
```

| What | URL | Sign-in |
|---|---|---|
| DP portal | http://localhost:5174 | `dp-demo@cm.local`: the code arrives in Mailpit |
| CM Admin | http://localhost:5176 | `admin@cm.local`: the code arrives in Mailpit |
| DF app | http://localhost:5175 | the fiduciary's API client secret, issued at activation |
| Mailpit (captured email) | http://localhost:8025 | none |
| Keycloak admin console | http://localhost:8080 | `admin` / `admin` |

Signing in is passwordless: enter an email address or mobile number and the 6-digit code sent to it. An address
without an account becomes a data principal account by signing in. See
[infra/keycloak/realm-export/README.md](infra/keycloak/realm-export/README.md).

To deliver email, SMS and push through notification-service instead of the local catchers, follow
[infra/notification-service/README.md](infra/notification-service/README.md).

## Verify it

```bash
scripts/verify-phase1.sh                 # the consent lifecycle end to end, through the gateway
node scripts/verify-otp-login.mjs        # passwordless sign-in: wrong code, existing account, new DP account
```

## Layout

| Path | What |
|---|---|
| `backend/services/<svc>` | One Spring Boot service, split into `-dom`, `-app`, `-infra`, `-if-rest` and `-boot` modules |
| `backend/libs` | Shared kernels: domain building blocks, events, web and security helpers, test base classes |
| `backend/build-logic` | Gradle convention plugins |
| `backend/keycloak-ext/cm-idn-otp-spi` | Keycloak authenticator for one-time-code sign-in |
| `backend/keycloak-ext/cm-idn-theme` | Keycloak login themes, `cm-base` and one per audience |
| `frontend/packages` | The three micro-frontends and their shared packages |
| `infra` | Docker Compose stack, Keycloak realm, OPA policies, Pulsar setup |
| `docs/adr` | Architecture decision records |
| `scripts` | Verification and project-management scripts |

## Contributing

Read [CONTRIBUTING.md](CONTRIBUTING.md): branches and pull requests only, Conventional Commits, tests first, an ADR
for every significant decision. Report security problems privately as it describes.

## License

[Apache License 2.0](LICENSE).
