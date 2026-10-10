# ADR-001: One service per bounded context, in the language of the DPDPA

- **Status:** Accepted
- **Date:** 2026-08-30 (Phase 1), recorded 2026-10-10

## Context

A consent manager under India's Digital Personal Data Protection Act has a few clearly separate concerns: onboarding
the organisations that ask for consent (Data Fiduciaries), the consent lifecycle itself, showing people what they
have consented to, notifying them, and billing fiduciaries. They change for different reasons and at different rates:
the consent lifecycle is the core domain and must be correct; billing and notification are supporting; identity is
generic and bought in (Keycloak).

## Decision

1. **Domain-driven design, with one Spring Boot service per bounded context:**

   | Bounded context | Service | Kind | Aggregates |
   |---|---|---|---|
   | Fiduciary registry | `cm-df-reg-svc` | Supporting | `Df` |
   | Consent lifecycle (write side) | `cm-cns-cmd-svc` | **Core** | `CnsReq`, `CnsArt` |
   | Consent views (read side) | `cm-cns-qry-svc` | Core (read model) | projections, no aggregates |
   | Notification | `cm-ntf-svc` | Supporting | `PushDvc` |
   | Billing | `cm-bil-svc` | Supporting | `DfSub`, `SubPln` |
   | Identity | Keycloak + `cm-idn-otp-spi` | Generic | Keycloak users |
   | Edge (PEP) | `cm-gw` | Generic | none |

2. **Tactical patterns from a shared kernel** (`cm-dom-kit`): aggregate roots extend `Agg`, which collects the
   `DomainEvt`s raised by a mutation; business rules are `Spec` specifications (`CnsReqDecidablePolicy`,
   `DfSubEligiblePolicy`, `CnsArtValidPolicy`) that return a reason, never a bare boolean, so every rejection maps to a
   `problem+json` type; construction goes through factories (`CnsReqFactory`, `CnsArtFactory`); persistence through
   repository ports implemented in `-infra`. Value objects are records (`Prps`, `DCat`, `Scp`, `ValPrd`, `DpRef`).
3. **The ubiquitous language is abbreviated and used everywhere:** in class names, tables, topics and APIs (`CnsReq`
   for consent request, `CnsArt` artefact, `CnsTkn` token, `Df` fiduciary, `Dp` principal, `Prps` purpose, `DCat`
   data category). The glossary is in [design.md](../design.md#glossary).
4. **Contexts integrate only through events and explicit internal endpoints.** No service reads another's tables;
   integration goes through Pulsar events from a transactional outbox ([ADR-004](adr-004-transactional-outbox-and-pulsar.md))
   or a named `/internal/**` endpoint (`cm-cns-qry-svc`'s artefact status for the PDP, `cm-ntf-svc`'s OTP delivery,
   `cm-bil-svc`'s eligibility check).

## Consequences

- Each context can change, scale and be released on its own, and the core domain carries no billing or notification
  code.
- Cross-context consistency is eventual: a newly activated fiduciary or a just-approved consent appears in other
  contexts after its event is processed. The UIs poll briefly where a user expects to see their own write
  ([ADR-003](adr-003-cqrs-consent-command-and-query.md)).
- The abbreviations are a cost for newcomers; the glossary is the mitigation, and new names must follow it.
- Physically the contexts still share one PostgreSQL instance, one schema each
  ([ADR-007](adr-007-reactive-stack-and-schema-per-service.md)).
