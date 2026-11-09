# ADR-103: The sign-in service key is required in the cloud values

- **Status:** Accepted
- **Date:** 2026-11-09
- **Issue:** #105
- **Completes:** the "optional by default" limit of [ADR-083](adr-083-key-for-requesting-a-sign-in-code.md)

## Context

ADR-083 made `POST /internal/otp/send` need a key, but left the key optional in both charts: a deployment that never set it stayed open,
with only a warning at start-up that nobody reads. ADR-083 gave the reason: making it required everywhere would stop an upgrade of a
cluster that had not yet created the secret. A cloud deployment is not that case. It is a new install from the cloud values, where
the secret store is set up first, and an endpoint that sends messages to anyone, open by omission, is the wrong default there.

## Decision

1. **`secrets.requireInternalKey`** (consent-manager chart) and **`keycloak.otp.requireApiKey`** (common chart). When on, the chart
   reads `NTF_INTERNAL_API_KEY` from the secret **without** `optional`: a pod whose secret lacks the key does not start
   (`CreateContainerConfigError`), and the External Secrets sync, which now maps the key, fails when the store has no value for it.
2. **Off in the base values, on in `values-aws`, `values-azure` and `values-gcp`** of both charts. A local or a first install on the
   base values starts without the key, with the warning, exactly as before.
3. **The key is mapped in every cloud's External Secrets** (AWS `NTF_INTERNAL_API_KEY` in the application secret, Azure
   `ntf-internal-api-key`, GCP `cm-production-ntf-internal-api-key`), and GCP's Terraform makes the empty Secret Manager entry
   beside the others an operator fills (`ntf-internal-api-key`). The deployment guide's table says what it is.
4. **The same value goes in both namespaces' stores**, because the notification service (consent-manager chart) and Keycloak (common
   chart) each read it.

## Checked

- For each cloud, both charts render with the key's secret reference **without** `optional` and with the External Secrets mapping the key;
  with the base values the reference is still `optional: true`. kubeconform passes for all six renders.
- GCP's Terraform module tests pass with the new Secret Manager entry (the test that pins the list was updated), `fmt` passes for all
  three clouds, and the GCP root validates.

## Not yet / limits

- **Not applied to a cluster:** the failure mode ("the pod does not start without the key") is Kubernetes' documented behaviour for a
  non-optional secret key reference, not something observed here.
- **Terraform does not generate the key,** it only provides the place (GCP) or the documentation (AWS, Azure): an operator makes a long random
  value, like the notification-service credentials.
- **The previous key (ADR-101) is not mapped:** it is only for the length of a rotation, and a mapping to a store entry that does not exist
  would fail the sync the rest of the time. To rotate, add `NTF_INTERNAL_API_KEY_PREVIOUS` to the External Secrets data and the store for the
  duration, then remove it.
- **The application still starts without a key if the chart is told it may** (the base values), and the filter is still open when its
  setting is empty: this is the chart's decision, not a check in the service.
