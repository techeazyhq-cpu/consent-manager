# ADR-054: The common namespace on Kubernetes: one chart for the shared servers, the apps in their own namespaces

- **Status:** Accepted
- **Date:** 2026-10-20
- **Implements:** [ADR-053](adr-053-common-servers-for-both-applications.md) (one common set of servers for consent-manager and
  notification-service) on Kubernetes
- **Relates to:** [ADR-042](adr-042-deploying-to-aws-azure-and-gcp.md) (deploying to AWS, Azure and GCP),
  [ADR-037](adr-037-running-the-sandbox.md) (the sandbox), [ADR-035](adr-035-a-keycloak-realm-per-audience.md) (a
  realm per audience), [ADR-052](adr-052-resource-limits-for-the-local-stack.md) (resource limits for the local stack)

## Context

consent-manager delivers through the user's own notification-service, which needs the same kinds of servers:
PostgreSQL, Redis and Pulsar, and Mailpit while developing. Each project brought its own. On one development machine
that meant two Pulsars, two PostgreSQLs and two Redises; notification-service's k3s setup already put its datastores in
a `datastores` namespace, while this chart expected PostgreSQL, Redis, Pulsar and Keycloak as unnamed external services
in its own namespace, with no way to run them on a dev or test cluster at all.

ADR-053 shares PostgreSQL, Redis, Pulsar, Keycloak and Mailpit in one `common` set of servers, with the contract
below; locally that is the `infra/common` compose project. This record is the Kubernetes side.

## Decision

1. **Three namespaces.** `common` holds the shared servers, `consent-manager` this repository's chart, and
   `notification` notification-service's chart (changed in its own repository).
2. **The apps reach the servers by fixed names**, `<name>.common.svc.cluster.local`:
   | Server | Port | Each app's share |
   |---|---|---|
   | `postgres` | 5432 | database `consent_manager` (user `cm`); `notification` (owner `notification`, app role `notification_app`, DML only); `keycloak`; `cm_sandbox` for a sandbox |
   | `redis` | 6379 | database 0 consent-manager, 1 its sandbox, 2 notification-service |
   | `pulsar` | 6650, admin 8080 | tenants `cm` and `cm-sbx`; notification-service uses `public/default`, kept 60 minutes / 1024 MB |
   | `keycloak` | 8080 | the realms of `infra/keycloak/realm-export` and the `cm-idn` providers |
   | `mailpit` | 1025, UI 8025 | everything sent while developing, and the sandbox's mail |
3. **A new chart, `deploy/helm/common`**, for development and test clusters (k3s, kind):
   - each server can be switched off (`enabled`), runs one replica with a persistent volume (Mailpit keeps its inbox in
     memory), and has requests, limits and probes sized like the local stack's (ADR-052)
   - Pulsar is checked over HTTP (`/admin/v2/brokers/health`), never with a `pulsar-admin` JVM, and advertises its pod's
     DNS name so clients in other namespaces can reach the broker a lookup sends them to
   - a post-install and post-upgrade Job creates the roles and databases (setting each role's password from the Secret,
     so an upgrade rotates it), the Pulsar tenants and namespaces (`infra/pulsar/init-tenant.sh`'s logic) and
     `public/default`'s retention; with everything there it changes nothing
   - Keycloak imports the realms and loads the providers from ConfigMaps made from this repository; on start the realm
     files are rewritten so that sign-in codes go to `cm-ntf-svc` in the `consent-manager` namespace (the files keep
     compose's `http://cm-ntf-svc:8084`)
   - secrets come from one Secret, `common-secrets`, created by hand on a dev cluster or synced by External Secrets as
     in the consent-manager chart
4. **Only the apps get in.** `common` denies all ingress except from itself, from namespaces whose
   `kubernetes.io/metadata.name` is `consent-manager` or `notification`, and from the ingress controller's namespace to
   Keycloak's HTTP port. A sandbox release adds `consent-manager-sandbox` to that list (`networkPolicy.clientNamespaces`).
5. **The consent-manager chart deploys to `consent-manager`** and points at those names by default.
   - Its pods may send only to DNS, the release itself, `common`, the `notification` namespace, public addresses
     (fiduciaries' webhooks, ADR-047), and what `networkPolicy.datastoreEgress` adds: on a cloud, Terraform's
     `helm_values` output with the managed PostgreSQL's and Redis's private ranges.
   - `cm-ntf-svc` admits Keycloak from `common`, which posts sign-in codes to it.
   - The chart now sets the Redis database index and the SMTP host, which the services' `docker` profile used to fix,
     so the sandbox keeps its own index (1) on the shared Redis and mails the shared Mailpit.
6. **On a cloud, PostgreSQL and Redis stay managed** and outside the cluster, as before. `common` holds Keycloak from
   this chart (`values-<cloud>.yaml`: the databases off, Keycloak on the managed PostgreSQL) and Pulsar from the Apache
   Pulsar chart, aliased as `pulsar` by an ExternalName Service so the services' settings are the same everywhere. The
   Terraform outputs no longer point at a `pulsar` namespace.

## Consequences

- One PostgreSQL, Redis and Pulsar for both apps on a development cluster: about 3.7 GB of limits for `common` against
  two of each before. Each app still has its own database, roles, Redis index and Pulsar tenant or namespace.
- The apps share failure: a broken `common` stops both. Acceptable for development and test; production keeps managed
  datastores and a replicated Pulsar.
- notification-service's chart must point at the same names, create its Secret in `notification`, and use Redis
  database 2. Its broker-wide retention becomes `public/default`'s namespace retention here, so consent-manager's
  tenants keep Pulsar's default.
- The sandbox shares Mailpit with development mail. Nothing in Mailpit is ever delivered, so ADR-037's promise holds;
  only the inbox is shared.
- The realm files still name compose's hosts; the Keycloak init container rewrites two URLs. A realm that names
  another compose host needs the same treatment.
- The new chart is checked locally (`helm lint`, `helm template`, kubeconform). CI checks only the consent-manager
  chart until `.github/workflows/ci.yml` adds the common chart's three renders.
- Where the managed Redis offers only database 0 (Azure Managed Redis with clustering), a sandbox needs its own Redis.
