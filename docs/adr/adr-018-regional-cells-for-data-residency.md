# ADR-018: Regional cells for data residency

- **Status:** Proposed
- **Date:** 2026-10-11
- **Design:** [compliance architecture §5, §6.1](../compliance/compliance-architecture.md)

## Context

The platform serves principals under DPDPA (India), GDPR (EU/UK), HIPAA (US) and Gulf laws (UAE federal, DIFC, ADGM,
Saudi Arabia, Qatar, Bahrain, Oman).

- **India:** a registered Consent Manager must be an Indian company. Transfers out of India follow a negative list.
- **Saudi Arabia:** the PDPL conditions every transfer out of the Kingdom on adequacy or safeguards, with a risk
  assessment for sensitive data.
- **UAE:** health data is to be kept in the UAE **[confirm]**.
- **GDPR:** transfers need a Chapter V mechanism.

Today one database, one event bus, one Keycloak realm, one log pipeline and one backup set hold everyone. Partitioning
each of those stores by jurisdiction is possible in principle, but every store, log and backup would have to be
partitioned correctly, forever, and proven so.

## Decision

1. **A cell is one complete, independent deployment of the data plane** in one jurisdiction's cloud region: every
   service, PostgreSQL, Pulsar, the Keycloak realm, KMS keys, the notification-service instance and its providers,
   logs, traces and backups. Gulf health data uses in-country regions.
2. **Tenants and principals belong to exactly one cell.** A fiduciary serving several jurisdictions has one tenancy per
   cell. A person with relationships in two cells has two accounts.
3. **A small global control plane holds no personal data:**
   - the cell directory (tenant id → cell)
   - images, IaC and the pipeline
   - regime-profile data (ADR-020)
   - aggregate, anonymous fleet metrics
4. **Only these cross a cell boundary:** what point 3 lists, and RFC 3161 anchoring (hashes only). Network policy
   enforces it, and a CI isolation probe tests it.
5. **Initial cells:** IN, EU (with the UK unless counsel decides otherwise), US, SA, AE (with DIFC and ADGM tenants
   under their own regime profiles). Qatar, Bahrain and Oman get cells when a tenant needs them.
6. **Durability and recovery, per cell** (finding R18):
   - **Targets:** RPO at most 5 minutes and RTO at most 4 hours. These are estimates to validate against the
     verification SLO (ADR-027).
   - **Within the cell:** backups, replicas and the object-locked (WORM) archive of consent records (ADR-025) stay in
     the cell's jurisdiction. The archive lives in a second availability zone, or a second in-country region where
     one exists.
   - **Restore drills:** at least quarterly. Every restore re-applies the list of shredded subjects (ADR-019) before
     serving.

## Options considered

- **One global deployment, geo-partitioned data:** the cheapest to run. Rejected: residency would depend on every store,
  log, topic and backup being partitioned correctly, and it fails Gulf health localisation.
- **A product per jurisdiction:** maximum local fit. Rejected: forks diverge, and the cost is in people.

## Consequences

- Residency holds by construction, and a regional failure stays in its cell.
- Fixed cost grows with each cell, and operations must handle N cells. Identical IaC and one pipeline bound that.
- No cross-cell view of a person. A multinational fiduciary sees each cell separately.
- Global features (fleet dashboards) must be built from anonymous aggregates only.

## Compliance impact

- DPDPA s.16 and the Consent Manager conditions.
- KSA PDPL transfer rules.
- UAE health data localisation.
- GDPR Chapter V.
- HIPAA: BAA scope limited to the US cell.

## Follow-ups

- IaC module for a cell.
- Cell directory service.
- Isolation probe in CI.
- Per-cell notification providers (for example India SMS DLT registration).
