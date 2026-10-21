# Deploying Consent Manager

Terraform builds the cloud (network, Kubernetes, PostgreSQL, Redis, secrets, backups and alerts), and two Helm charts
deploy onto it. The same design runs on AWS, Azure and GCP ([ADR-042](../docs/adr/adr-042-deploying-to-aws-azure-and-gcp.md)),
and on a development or test cluster (k3s, kind).

A cluster has three namespaces ([ADR-054](../docs/adr/adr-054-common-namespace-on-kubernetes.md)):

| Namespace | What | Chart |
|---|---|---|
| `common` | The servers consent-manager and notification-service share | `deploy/helm/common` |
| `consent-manager` | The gateway, the services, OPA and the web apps | `deploy/helm/consent-manager` |
| `notification` | notification-service | its own repository's chart |

The apps reach the shared servers as `<name>.common.svc.cluster.local`:

| Server | Port | Each app's share |
|---|---|---|
| `postgres` | 5432 | database `consent_manager` (user `cm`), and `cm_sandbox` for a sandbox; `notification` (owner `notification`, app role `notification_app`); `keycloak` |
| `redis` | 6379 | database 0 consent-manager, 1 its sandbox, 2 notification-service |
| `pulsar` | 6650, admin 8080 | tenants `cm` and `cm-sbx`; notification-service uses `public/default`, kept 60 min / 1024 MB |
| `keycloak` | 8080 | the realms of `infra/keycloak/realm-export`, with the `cm-idn` providers |
| `mailpit` | 1025, UI 8025 | everything either app sends while developing, and the sandbox's mail |

Only pods in the namespaces `consent-manager` and `notification` may reach `common`, and the ingress controller may
reach Keycloak's public route. On a cloud, PostgreSQL and Redis are the managed services Terraform builds, outside the
cluster; `common` holds Keycloak (the common chart) and Pulsar (the Apache Pulsar chart).

```text
deploy/
├── terraform/
│   ├── aws/    production/ (VPC, EKS, identities) + modules/datastores/ (RDS, ElastiCache, KMS, Secrets Manager, backups)
│   ├── azure/  production/ (VNet, AKS, identities) + modules/datastores/ (Flexible Server, Managed Redis, Key Vault, backups)
│   └── gcp/    production/ (VPC, GKE, identities) + modules/datastores/ (Cloud SQL, Memorystore, Secret Manager, backups)
└── helm/
    ├── common/                the shared servers, each switchable (enabled), and their init Job
    │   ├── values.yaml        a dev or test cluster: all five, with persistent volumes
    │   └── values-aws.yaml    a cloud: Keycloak on the managed PostgreSQL, Pulsar aliased from its own chart
    │       values-azure.yaml, values-gcp.yaml
    └── consent-manager/
        ├── values.yaml            the gateway, eight services, OPA, three web apps, on common's servers
        ├── values-aws.yaml        what differs on each cloud: endpoints, ingress, the secret store
        ├── values-azure.yaml
        ├── values-gcp.yaml
        └── values-sandbox.yaml    a sandbox release (ADR-037), merged over values.yaml or the cloud's file
```

## 1. Build the cloud

```bash
cd deploy/terraform/aws/production    # or azure/production, gcp/production
terraform init                        # configure your own remote state backend first
terraform plan -out plan.tfplan
terraform apply plan.tfplan
```

Variables (region, recovery region, sizes, alert email, who may reach the cluster API) are in each `variables.tf`.
Terraform creates the secret entries without values. An operator sets them once:

| Key | What |
|---|---|
| `DB_PASSWORD` | The services' database password (`MIGRATION_DB_PASSWORD` is the schema owner's, managed by the cloud) |
| `DFREG_SECRETS_KEY` | Base64 of 32 random bytes: seals API key signing secrets (ADR-041) |
| `CNS_TKN_JWKS` | The consent token signing key set (ADR-006), a JSON Web Key Set with private keys |
| `CNSCMD_SECRETS_KEY` | Base64 of 32 random bytes: seals webhook signing secrets (ADR-047) |
| `CNS_RCPT_JWKS` | The consent receipt signing key set (ADR-025), the same form, never the same keys. Keep retired keys in it, after the current one, for as long as receipts they signed are kept |
| `KEYCLOAK_ADMIN_PASSWORD`, `KEYCLOAK_DB_PASSWORD` | Keycloak's admin and database passwords (read by both charts' Secrets) |
| `NTF_SVC_API_KEY`, `NTF_SVC_SIGNING_SECRET` | The notification-service client credentials |

## 2. The platform pieces outside the charts

- **External Secrets Operator**, with a `ClusterSecretStore` named as in `values-<cloud>.yaml`.
- **An ingress controller** (ingress-nginx) and **cert-manager** with a `letsencrypt` ClusterIssuer.
- **Pulsar:** the Apache Pulsar chart, as release `pulsar` in the namespace `common`. The common chart aliases its
  broker as `pulsar`, and its init Job creates the tenants and namespaces (`infra/pulsar/init-tenant.sh`'s logic).

## 3. The shared servers

Keycloak's realms and providers go in as ConfigMaps made from this repository. On start, the realm files are rewritten
so that sign-in codes go to `cm-ntf-svc` in the `consent-manager` namespace (`keycloak.otp`). Build the providers first
(`./gradlew build` puts them in `backend/keycloak-ext/build/providers`):

```bash
kubectl create namespace common
kubectl -n common create configmap keycloak-realms --from-file=infra/keycloak/realm-export
kubectl -n common create configmap keycloak-providers --from-file=backend/keycloak-ext/build/providers
helm upgrade --install common deploy/helm/common -n common -f deploy/helm/common/values-aws.yaml \
  --set keycloak.database.host=<terraform output> --timeout 15m
```

The Secret `common-secrets` comes from the secret store (`KEYCLOAK_DB_PASSWORD`, `KEYCLOAK_ADMIN_PASSWORD`). The init
Job creates consent-manager's Pulsar tenants and namespaces and `public/default`'s retention after every install and
upgrade. Keycloak's `hostname` is the public URL in the consent-manager chart's `config.keycloak.publicUrl`.

## 4. Deploy the services

Terraform's `helm_values` output holds the network policy rules for the managed PostgreSQL and Redis
(`networkPolicy.datastoreEgress`); without them the services can't reach either.

```bash
terraform -chdir=deploy/terraform/aws/production output -json helm_values > helm-values.json
helm upgrade --install cm deploy/helm/consent-manager -n consent-manager --create-namespace \
  -f deploy/helm/consent-manager/values-aws.yaml -f helm-values.json \
  --set config.database.host=<terraform output> --set config.redis.host=<terraform output> \
  --set image.tag=0.1.0
```

Build the web app images per environment:

```bash
docker build -f infra/docker/Dockerfile.ui --build-arg APP=mf-df-app --build-arg VITE_GW_BASE_URL=https://api.example.com --build-arg VITE_KC_BASE_URL=https://id.example.com --build-arg VITE_KC_REALM=cm-df --build-arg VITE_KC_CLIENT_ID=cm-df-app -t ghcr.io/techeazyhq-cpu/cm-df-app:0.1.0 .
```

A sandbox is a second release in the namespace `consent-manager-sandbox`, with its own database (`cm_sandbox`), Redis
database index (1), Pulsar tenant (`cm-sbx`) and realms (`sbx-`) on the shared servers. Let its namespace into
`common`, and on a cloud run Mailpit there for its mail, which is never delivered:

```bash
helm upgrade common deploy/helm/common -n common --reuse-values --timeout 15m \
  --set 'networkPolicy.clientNamespaces={consent-manager,notification,consent-manager-sandbox}' --set mailpit.enabled=true
helm upgrade --install cm deploy/helm/consent-manager -n consent-manager-sandbox --create-namespace \
  -f deploy/helm/consent-manager/values-aws.yaml -f deploy/helm/consent-manager/values-sandbox.yaml -f helm-values.json
```

## A development or test cluster (k3s, kind)

Everything runs in the cluster: the common chart's defaults start all five servers with persistent volumes, and the
consent-manager chart's defaults point at them. Single replicas, with requests and limits in the spirit of the local
stack's ([ADR-052](../docs/adr/adr-052-resource-limits-for-the-local-stack.md)): `common` is limited to about 3.7 GB
of memory. The cluster must enforce NetworkPolicies (k3s does; kind needs a CNI such as Calico).

```bash
kubectl create namespace common
kubectl -n common create configmap keycloak-realms --from-file=infra/keycloak/realm-export
kubectl -n common create configmap keycloak-providers --from-file=backend/keycloak-ext/build/providers
kubectl -n common create secret generic common-secrets \
  --from-literal=POSTGRES_PASSWORD=postgres --from-literal=CM_DB_PASSWORD=cm \
  --from-literal=NOTIFICATION_DB_PASSWORD=notification --from-literal=NOTIFICATION_APP_DB_PASSWORD=notification_app \
  --from-literal=KEYCLOAK_DB_PASSWORD=keycloak --from-literal=KEYCLOAK_ADMIN_PASSWORD=admin
helm upgrade --install common deploy/helm/common -n common --timeout 15m
```

Then consent-manager, whose `DB_PASSWORD` is `CM_DB_PASSWORD` (the other keys as in step 1):

```bash
kubectl create namespace consent-manager
kubectl -n consent-manager create secret generic cm-secrets --from-literal=DB_PASSWORD=cm \
  --from-literal=KEYCLOAK_ADMIN_PASSWORD=admin --from-literal=DFREG_SECRETS_KEY="$(openssl rand -base64 32)" \
  --from-literal=CNSCMD_SECRETS_KEY="$(openssl rand -base64 32)" \
  --from-file=CNS_TKN_JWKS=<a JWK set> --from-file=CNS_RCPT_JWKS=<another JWK set>
helm upgrade --install cm deploy/helm/consent-manager -n consent-manager --set image.tag=0.1.0 \
  --set config.keycloak.publicUrl=http://localhost:8080 --set config.notificationService.provider=local
```

notification-service's own Secret, in the `notification` namespace, carries `NOTIFICATION_DB_PASSWORD` and
`NOTIFICATION_APP_DB_PASSWORD`. The init Job sets every role's password from `common-secrets` on each upgrade, so
changing one there and upgrading rotates it. Keycloak's admin console and Mailpit are behind
`kubectl -n common port-forward svc/keycloak 8080` and `svc/mailpit 8025`.

## Checks (run in CI)

```bash
docker run --rm -v "$PWD/deploy/terraform:/tf" -w /tf --entrypoint sh hashicorp/terraform:1.12.2 -c 'terraform fmt -check -recursive'
docker run --rm -v "$PWD/deploy/helm:/charts" -w /charts alpine/helm:3.16.4 lint consent-manager -f consent-manager/values-aws.yaml
docker run --rm -v "$PWD/deploy/helm:/charts" -w /charts alpine/helm:3.16.4 lint common -f common/values-aws.yaml
```

When `infra/opa/policies/cns_authz.rego` changes, copy it to `deploy/helm/consent-manager/files/opa/`. CI fails until
the two match.
