# ADR-021: Principal requests: rights over the platform's own data, and relay to fiduciaries

- **Status:** Proposed
- **Date:** 2026-10-11
- **Design:** [compliance architecture §1, §6.7](../compliance/compliance-architecture.md)

## Context

The platform is a consent manager only ([ADR-023](adr-023-pure-consent-manager-boundary-and-neutrality.md)), but it
is also a data fiduciary or controller for what it holds about its own users: their contact points, account, consent
records and representatives. Those users have rights over that data in every regime. Separately:

- Under DPDPA, a principal may be able to exercise rights against a fiduciary through the consent manager **[confirm
  Rule 14]**.
- MeitY's consent-management BRD expects simple forms for access, correction and erasure requests, with escalation.

The fiduciary, not the platform, holds the data those requests concern. Erasure of the platform's own data must reach
every context and Keycloak, yet keep the pseudonymised consent records a legal hold requires:

- DPDPA consent managers: at least 7 years (First Schedule B4).
- HIPAA authorisation documentation: 6 years.

## Decision

1. **A new service, `cm-dsr-svc` (Principal Requests), owns `PrincipalRequest`,** of three kinds:
   - **Own-data rights:** access (every record and receipt, ADR-025), correction (contact points, in the vault),
     erasure (close the account).
   - **Relayed requests:** a request about a fiduciary's data, passed to that fiduciary by webhook and in the DF app.
     The platform records when it was relayed and what the fiduciary answered, and never fulfils it itself.
   - **Grievances about the platform**, with the regime's deadline and the escalation route (the Board, the
     supervisory authority).
2. **Invariants:**
   - Deadlines are fixed at receipt from the regime profile (ADR-020).
   - Nothing is fulfilled before identity or a representative's authority is verified.
   - Refusals cite a legal ground.
3. **Own-data erasure is an orchestrated saga**, run in this order:
   1. notice
   2. withdraw active consents (fiduciaries get change webhooks)
   3. delete push devices
   4. erase audit payloads and subject links
   5. delete the Keycloak user
   6. shred the subject key (ADR-019)

   Steps are idempotent and retried. Shredding is the point of no return and comes last. A step that can't complete
   moves the request to `STUCK` with an alert and a runbook.
4. **Legal holds keep the pseudonymised consent records** and remove contact data. The principal is told what is held,
   why and until when.

## Options considered

- **A full rights-fulfilment hub for fiduciaries' data:** rejected. It is not a consent manager's function, and it
  would put the platform in the path of the governed data (DPDPA First Schedule B2).
- **Choreographed erasure:** no single view of progress or deadline, and no way to order the point of no return last.

## Consequences

- The platform honours its own users' rights end to end, and gives principals one place to send requests without
  taking on fiduciaries' duties.
- One more service per cell.
- Fiduciaries must integrate the relay webhook; the DF app is the fallback.

## Compliance impact

- Rights over the platform's own data: DPDPA ss.11-13, GDPR Arts. 12-20, and the Gulf laws' equivalents.
- DPDPA First Schedule B4.
- Relay only, where DPDPA Rule 14 permits it **[confirm]**.
