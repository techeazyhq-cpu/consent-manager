# ADR-053: One common set of servers for consent-manager and notification-service

- **Status:** Accepted
- **Date:** 2026-10-20
- **Relates to:** [ADR-052](adr-052-resource-limits-for-the-local-stack.md) (limits),
  [ADR-037](adr-037-running-the-sandbox.md) (the sandbox already shares servers)

## Context

consent-manager delivers through the user's own notification-service. Locally each ran its own Postgres, Redis,
Pulsar and Mailpit: two of each server on one laptop, about 1.2 GB of memory for no benefit, and two inboxes to look
in. In Kubernetes, each chart expected the servers in its own namespace.

## Decision

1. **Three namespaces, the same names locally and in Kubernetes:**
   - `common`: the shared servers
   - `consent-manager`: this repository's applications
   - `notification`: notification-service's applications

   Locally each is a docker compose project. `common` creates the Docker network `common`, and both app projects join
   it. In Kubernetes they are namespaces; `common` admits only the two app namespaces (and the ingress controller,
   for Keycloak's public route).
2. **One of each server; each app separated within it.** Every app finds the servers by the same hostnames it used
   before, so no application code changed:

   | Server | consent-manager | notification-service |
   |---|---|---|
   | Postgres 16 | database `consent_manager`, user `cm`; `cm_sandbox` | database `notification`, owner `notification`, app role `notification_app` (rows only) |
   | Redis 7 | index 0; sandbox 1 | index 2 (`REDIS_DATABASE`) |
   | Pulsar 3.3.3 | tenants `cm` and `cm-sbx` | `public/default`, retention 60 min / 1 GB |
   | Keycloak 25 | its realms, in database `keycloak` | none |
   | Mailpit | one inbox for both | |

   What only one app needs stays with it: OPA and the sandbox inbox with consent-manager; the push catcher and the
   console UIs with notification-service.
3. **Locally:** `infra/common/compose.yml` (this repository) runs the servers, sets up the databases and roles on
   first start, and creates the Pulsar tenants on every start. Start order: common (`up -d --wait`), then either app.
   notification-service keeps its standalone mode; its shared mode is an override in its own repository.
4. **Keycloak keeps its data** in its own database on the shared Postgres. Before, `start-dev` kept everything in an
   in-container H2: every recreate re-imported the realms, gave every user a new id, and lost every fiduciary member
   created since.
5. **Kubernetes:** a `common` chart for development and test clusters. In the cloud, managed Postgres and Redis stay
   outside the cluster, and `common` holds Pulsar and Keycloak. Both app charts address
   `<server>.common.svc.cluster.local`.

## Consequences

- Locally, one Postgres, Redis, Pulsar and Mailpit fewer, and every message either app sends lands in one inbox
  (http://localhost:8025).
- Each app's data stays separate (databases, roles, Pulsar tenants, Redis indexes), but a server outage affects both.
  This is acceptable for development; production runs the shared servers highly available or managed.
- Moving existing local data needs a one-off dump and restore; Pulsar and Keycloak start fresh.
- The common servers have no single owner app: changes to them (versions, limits) are made here, in
  `infra/common` and `deploy/helm/common`.
