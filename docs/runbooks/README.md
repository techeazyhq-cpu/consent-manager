# Runbooks

What to do, in order, for the things an operator of the platform does or is asked about. Each one names the decision it follows.

| Runbook | When |
|---|---|
| [Releasing and installing a version](release.md) | cutting a release, verifying an image, its bill of materials and provenance, installing, rolling back ([ADR-072](../adr/adr-072-release-pipeline.md), [ADR-086](../adr/adr-086-release-bill-of-materials-and-provenance.md)) |
| [Backup and recovery](backup-and-recovery.md) | the dumps, the restore drill, the recovery targets, losing a zone or a region |
| [Metrics, dashboards and alerts](observability.md) | running Prometheus, Grafana and Alertmanager, and what each alert means ([ADR-070](../adr/adr-070-metrics-and-dashboards.md), [ADR-087](../adr/adr-087-datastore-metrics-and-alert-routing.md)) |
| [Transport security](transport-security.md) | the edge's TLS and headers, mutual TLS between pods ([ADR-073](../adr/adr-073-transport-hardening.md)) |
| [Per-service database logins](database-roles.md) | giving each service a login that can only touch its schema ([ADR-082](../adr/adr-082-least-privilege-database-logins.md)) |
| [Moving a service to its own database](database-per-service.md) | rehearsing and cutting over a schema to its own database or server ([ADR-074](../adr/adr-074-database-per-service.md)) |
| [A fiduciary leaves](offboarding.md) | suspension, the wind-down, closing, retention and legal hold ([ADR-064](../adr/adr-064-fiduciary-offboarding.md)) |
| [A stuck account erasure](erasure.md) | an erasure saga that did not finish ([ADR-059](../adr/adr-059-closing-an-account-erasure-saga.md)) |
| [Invoicing](invoicing.md) | prices, the monthly run, marking paid, credit notes, the accounting export ([ADR-079](../adr/adr-079-invoicing.md), [ADR-084](../adr/adr-084-billing-ends-when-a-fiduciary-closes.md)) |
| [The authorisation policy bundle](opa-policy-bundles.md) | signing and publishing the policy, rotating its key ([ADR-068](../adr/adr-068-signed-policy-bundles.md)) |
| [Email deliverability](email-deliverability.md) | SPF, DKIM, DMARC for the sending domain; "the code never arrived" ([ADR-071](../adr/adr-071-email-deliverability-check.md)) |
