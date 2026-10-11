# consent-manager design

An overview of how the platform fits together. The reasons behind each choice are in the
[architecture decision records](adr/README.md). How it meets DPDPA, GDPR, HIPAA and the Gulf data protection laws, and
the planned changes for that, are in the [compliance architecture](compliance/compliance-architecture.md).

## Context

```text
 Data Principal ──(browser)──▶ DP portal ─┐
 Fiduciary staff ─(browser)──▶ DF app ────┤                    ┌──▶ Keycloak (identity, OTP sign-in)
 Administrator ──(browser)──▶ CM Admin ───┼──▶ cm-gw (PEP) ────┼──▶ OPA (PDP)
 Fiduciary systems ──(API, client credentials)─┘                └──▶ services ──▶ notification-service ──▶ email / SMS / WhatsApp / push
```

## Containers

| Container | Owns | Talks to |
|---|---|---|
| `cm-gw` | Routing, JWT validation, PEP for token verification, per-fiduciary burst limit | Keycloak (JWKS), OPA, Redis, every service |
| `cm-df-reg-svc` | `df_reg`: fiduciaries; outbox | Keycloak Admin API (provisions `df-api-<dfId>`), Pulsar |
| `cm-cns-cmd-svc` | `cns_cmd`: consent requests and artefacts, outbox, (saga state) | `cm-df-reg-svc`, `cm-bil-svc` (eligibility), Pulsar |
| `cm-cns-qry-svc` | `cns_qry`: read model, history, projection checkpoints | Pulsar (consumes consent events) |
| `cm-ntf-svc` | `ntf`: DP push devices | Pulsar (consumes notify commands), notification-service or Mailpit |
| `cm-bil-svc` | `bil`: plans, subscriptions; usage counters in Redis | Pulsar (consumes fiduciary activation), Redis |
| `cm-aud-svc` | `aud`: the tamper-evident audit ledger (hash chain, erasable payloads, checkpoints and their timestamps) | Pulsar (consumes every event), an RFC 3161 timestamp authority |
| Keycloak + `cm-idn-otp-spi`, `cm-idn-theme` | Users, clients, sessions | `cm-ntf-svc` (`/internal/otp/send`) |
| OPA | `cns_authz.rego` | `cm-cns-qry-svc` (`/internal/cns-arts/{id}/status`) |
| PostgreSQL | One database, one schema per service | |
| Apache Pulsar | Tenant `cm`, one namespace per context | |

## Consent lifecycle

```text
 DF raises request ──▶ CnsReq PENDING ──approve──▶ GRANTED ──▶ CnsArt ISSUED ──revoke──▶ REVOKED
   (DF active? subscription               ──deny────▶ DENIED                  ──expire──▶ EXPIRED
    eligible? checked first)              ──expire──▶ EXPIRED
```

1. **Capture.** A fiduciary raises a consent request for a principal, by CM identity or by email/phone. The request is
   refused up front if the fiduciary is inactive or its subscription is not eligible.
2. **Notice.** The saga sends `cmd-notify-dp`: email or SMS for a principal without an account yet, web push to each
   of their browsers for one who has signed in (the request is on their dashboard either way).
3. **Decision.** The principal approves or denies in the DP portal. Approval issues a consent artefact and a signed
   consent token for the fiduciary; the saga tells the fiduciary (`cmd-notify-df`).
4. **Enforcement.** Before processing, the fiduciary verifies: the gateway asks OPA, which checks the token and the
   artefact's live status.
5. **Withdrawal and expiry.** The principal can revoke at any time; artefacts past their validity are expired by a
   sweep. Both take effect for enforcement once projected.

## Events and commands

Topic names: `persistent://cm/<context>/<evt|cmd>-<name>-v1`.

| Topic | Kind | Producer | Consumers |
|---|---|---|---|
| `cm/dfreg/evt-df-activated-v1` | event | `cm-df-reg-svc` (outbox) | `cm-bil-svc`, `cm-aud-svc` |
| `cm/cnscmd/evt-cns-req-created-v1` | event | `cm-cns-cmd-svc` (outbox) | `cm-cns-qry-svc`, saga, `cm-aud-svc` |
| `cm/cnscmd/evt-cns-granted-v1` | event | `cm-cns-cmd-svc` (outbox) | `cm-cns-qry-svc`, saga, `cm-aud-svc` |
| `cm/cnscmd/evt-cns-denied-v1` | event | `cm-cns-cmd-svc` (outbox) | `cm-cns-qry-svc`, saga, `cm-aud-svc` |
| `cm/cnscmd/evt-cns-revoked-v1` | event | `cm-cns-cmd-svc` (outbox) | `cm-cns-qry-svc`, saga, `cm-aud-svc` |
| `cm/cnscmd/evt-cns-dp-linked-v1` | event | `cm-cns-cmd-svc` (outbox) | `cm-cns-qry-svc`, `cm-aud-svc` |
| `cm/cnscmd/evt-cns-expired-v1` | event | `cm-cns-cmd-svc` (outbox) | `cm-cns-qry-svc`, `cm-aud-svc` |
| `cm/ntf/cmd-notify-dp-v1` | command | saga | `cm-ntf-svc` |
| `cm/ntf/cmd-notify-df-v1` | command | saga | `cm-ntf-svc` |

Every message carries an `evtId`; consumers are idempotent on it.

## Patterns in use

| Pattern | Where |
|---|---|
| Bounded contexts, aggregates, value objects, domain events, specifications, factories, repositories (DDD) | `cm-dom-kit`; every `-dom` module |
| Clean architecture (ports and adapters), enforced by ArchUnit | `-dom` / `-app` / `-infra` / `-if-rest` / `-boot` |
| CQRS | `cm-cns-cmd-svc` writes, `cm-cns-qry-svc` reads |
| Transactional outbox, idempotent consumer | `ObxWriter`, `ObxRelay`, `prj_ckpt`, idempotency keys |
| Saga (orchestration / process manager) | `CnsLifecycleSagaOrchestrator` |
| Policy enforcement and decision points | `cm-gw` (PEP), OPA (PDP) |
| Circuit breaker | Every synchronous call to another service, in its `-infra` adapter or gateway filter, and every gateway route; `cm-res-kit` ([ADR-016](adr/adr-016-circuit-breakers-on-every-synchronous-call.md)) |
| Hash chain (tamper-evident, append-only ledger) | `cm-aud-svc`: `AudChain`, `AudChainVerifier`, `aud.entry` |
| Merkle checkpoints anchored by an RFC 3161 timestamp authority | `cm-aud-svc`: `AudMerkle`, `AudCheckpoint`, `Rfc3161TimestampAuthority`; how to verify: [audit-ledger.md](audit-ledger.md) |

## Glossary

| Term | Meaning |
|---|---|
| DPDPA | India's Digital Personal Data Protection Act, 2023 |
| `Dp` | Data Principal: the person the data is about |
| `Df` | Data Fiduciary: the organisation that asks for consent and processes the data |
| `CnsReq` | Consent request raised by a fiduciary |
| `CnsArt` | Consent artefact: the record of consent given, with its scope and validity |
| `CnsTkn` | Consent token: a signed JWT a fiduciary presents to prove consent |
| `Prps` | Purpose of processing (`prpsCode`) |
| `DCat` | Data category covered by the consent |
| `Scp` | Scope: the data categories of an artefact |
| `ValPrd` | Validity period of an artefact |
| `DpRef` | Reference to a principal: linked (`dpId`) or not yet (`EMAIL`/`PHONE` and value) |
| `Ntc` | Notice: the title and text shown to the principal |
| `Ntf` | Notification |
| `DfSub`, `SubPln` | A fiduciary's subscription and its plan |
| `Obx` | Outbox |
| `Sga` | Saga |
| `Prj`, `Ckpt` | Projection, checkpoint |
| PEP, PDP | Policy enforcement point, policy decision point |
