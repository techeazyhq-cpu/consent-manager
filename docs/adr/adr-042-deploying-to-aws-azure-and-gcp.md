# ADR-042: Deploying to AWS, Azure and GCP with Terraform and one Helm chart

- **Status:** Accepted
- **Date:** 2026-10-19

## Context

Consent Manager ran only on docker-compose. Fiduciaries in different jurisdictions need it hosted in their region and
cloud: DPDPA data in India, GDPR in the EU, and Middle East residency rules. Each deployment must be repeatable,
reviewable and the same everywhere.

TechEazyHQ's notification-service already deploys to all three clouds: Terraform per cloud (network, Kubernetes,
managed datastores, keys and secrets, backups and alerts) plus one Helm chart. Its modules are reviewed and carry
their own tests. Consent Manager has the same shape: stateless JVM services, PostgreSQL, Redis and Pulsar.

## Decision

1. **Terraform per cloud** (`deploy/terraform/{aws,azure,gcp}`), adapted from notification-service's:

   | | AWS | Azure | GCP |
   |---|---|---|---|
   | Network | Three-zone VPC, NAT per zone, flow logs | VNet, NAT gateway, private endpoints | VPC, Cloud NAT, private services access |
   | Kubernetes | EKS with VPC CNI network policies | AKS | GKE with Dataplane V2 |
   | PostgreSQL | RDS, TLS only, PITR, cross-region backups | Flexible Server, zone-redundant HA | Cloud SQL regional HA, private IP |
   | Redis | ElastiCache, two nodes | Azure Managed Redis | Memorystore |
   | Secrets and keys | Secrets Manager under KMS | Key Vault | Secret Manager, replicated in two regions |

   Workload identities (EKS Pod Identity, AKS workload identity, GKE workload identity) give External Secrets and the
   backup job only what they need.
   - **Consent Manager's secrets:** the database password, df-reg's sealing key (ADR-041), the consent token signing
     keys (ADR-006), Keycloak's admin and database passwords, and notification-service credentials.
   - **Databases:** `consent_manager` holds the services' schemas, beside a `keycloak` database.
   - **AWS egress:** nodes reach the internet on 443 only: the notification service, the timestamp authority that
     anchors the audit ledger (ADR-015), registries and cloud APIs. Notification-service also needs SMTP; Consent
     Manager doesn't.
   - **Tests:** each `datastores` module keeps its `terraform test` suite (mock providers, no cloud account needed).
2. **One Helm chart** (`deploy/helm/consent-manager`) for all three clouds, with `values-<cloud>.yaml` holding only
   what differs:
   - **Kubernetes objects:** the gateway and eight services as Deployments, Services, PodDisruptionBudgets and
     autoscalers; OPA with the consent policy; the three web apps; one Ingress where only the gateway's `/api` and the
     web apps are public.
   - **Network policies:** default-deny ingress, the ingress controller reaching only the gateway and web apps, and
     the release's pods reaching each other.
   - **Service names match docker-compose's** (`cm-gw`, `cm-df-reg-svc`, …), so each service keeps its tested `docker`
     Spring profile. The chart sets only the datastores, Keycloak and secrets, as environment variables.
   - **Hardening:** non-root, read-only root filesystems, no capabilities and RuntimeDefault seccomp on every pod.
     Consent token keys are mounted from the secret store and are never generated in a pod.
   - **Secrets** come from the cloud's store through External Secrets, never from values.
   - **Keycloak's database:** a pre-install job creates it where the cloud doesn't (RDS creates one database).
   - **The sandbox (ADR-037)** is the same chart in its own namespace with `values-sandbox.yaml`: its own database and
     Pulsar tenant, `sbx-` realms, no delivery, and no web apps.
3. **External to the chart:** PostgreSQL, Redis (managed, from Terraform), Pulsar (the Apache Pulsar chart or a
   managed service) and Keycloak (its operator or chart, importing `infra/keycloak/realm-export`). These have their
   own lifecycles and operators, and bundling them would make every upgrade of the services an upgrade of the platform.
4. **Web apps are built per environment** (`infra/docker/Dockerfile.ui`): Vite bakes the gateway and Keycloak
   addresses in. Unprivileged nginx serves them on 8080 with deep-link fallback, immutable caching for hashed assets
   and security headers.
5. **CI checks it all on every pull request:**
   - Terraform `fmt` and `validate` for each cloud, and the module test suites
   - Helm lint, a render for each cloud and the sandbox, and kubeconform against Kubernetes 1.30
   - that the chart's OPA policy matches the tested one
6. **Nothing is applied from CI.** An operator plans and applies each environment, with remote state set up per
   organisation.

## Options considered

- **Native tools per cloud** (CloudFormation or CDK, Bicep, Deployment Manager or Config Connector): three toolchains,
  and three different ways to express the same design.
- **Bundling the datastores in the chart:** convenient for a demo, wrong for production. Managed services carry the
  backups, failover and patching that compliance asks for.
- **Kustomize instead of Helm:** fine for overlays, but the per-service loops and per-cloud secret mappings read more
  clearly as one templated chart. Notification-service already uses Helm.

## Consequences

- One reviewed design for three clouds. A region or residency choice is a variable, not a rewrite.
- **Inherited conventions:** notification-service's choices come along (three zones, TLS-only datastores,
  cross-region backups, alerting). A change to one should be considered for the other.
- **Values to fill in:** each environment's endpoints are placeholders (`REPLACE_WITH_…`) until its Terraform outputs
  are known. The production stacks' outputs can feed them.
- **Not yet:** a Pulsar and Keycloak production setup in this repository (sizing, TLS, the realm import job), ingress
  WAF rules, and dashboards. Those follow.

## Compliance impact

- **Data residency:** every store, backup and secret is in the chosen region and its named recovery region.
- **Encryption:** in transit (TLS-only datastores, HTTPS ingress) and at rest (cloud KMS).
- **Least privilege:** per workload. Network isolation is per pod.
