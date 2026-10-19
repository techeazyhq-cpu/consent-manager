# Deploying Consent Manager

Terraform builds the cloud (network, Kubernetes, PostgreSQL, Redis, secrets, backups and alerts), and one Helm chart
deploys the services onto it. The same design runs on AWS, Azure and GCP ([ADR-042](../docs/adr/adr-042-deploying-to-aws-azure-and-gcp.md)).

```text
deploy/
├── terraform/
│   ├── aws/    production/ (VPC, EKS, identities) + modules/datastores/ (RDS, ElastiCache, KMS, Secrets Manager, backups)
│   ├── azure/  production/ (VNet, AKS, identities) + modules/datastores/ (Flexible Server, Managed Redis, Key Vault, backups)
│   └── gcp/    production/ (VPC, GKE, identities) + modules/datastores/ (Cloud SQL, Memorystore, Secret Manager, backups)
└── helm/consent-manager/
    ├── values.yaml            the gateway, eight services, OPA, three web apps
    ├── values-aws.yaml        what differs on each cloud: endpoints, ingress, the secret store
    ├── values-azure.yaml
    ├── values-gcp.yaml
    └── values-sandbox.yaml    a sandbox release (ADR-037), merged over the cloud's file
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
| `KEYCLOAK_ADMIN_PASSWORD`, `KEYCLOAK_DB_PASSWORD` | Keycloak's admin and database passwords |
| `NTF_SVC_API_KEY`, `NTF_SVC_SIGNING_SECRET` | The notification-service client credentials |

## 2. The platform pieces outside the chart

- **External Secrets Operator**, with a `ClusterSecretStore` named as in `values-<cloud>.yaml`.
- **An ingress controller** (ingress-nginx) and **cert-manager** with a `letsencrypt` ClusterIssuer.
- **Pulsar:** the Apache Pulsar chart, or a managed service. Create the tenant and namespaces with
  `infra/pulsar/init-tenant.sh` (`TENANT=cm`, and `cm-sbx` for a sandbox).
- **Keycloak:** its operator or chart, on the `keycloak` database, importing `infra/keycloak/realm-export`, with the
  `cm-idn` providers from `backend/keycloak-ext`. Set its hostname to the public URL in `config.keycloak.publicUrl`.

## 3. Deploy the services

```bash
helm upgrade --install cm deploy/helm/consent-manager -n consent-manager --create-namespace \
  -f deploy/helm/consent-manager/values-aws.yaml \
  --set config.database.host=<terraform output> --set config.redis.host=<terraform output> \
  --set image.tag=0.1.0
```

Build the web app images per environment:

```bash
docker build -f infra/docker/Dockerfile.ui --build-arg APP=mf-df-app --build-arg VITE_GW_BASE_URL=https://api.example.com --build-arg VITE_KC_BASE_URL=https://id.example.com --build-arg VITE_KC_REALM=cm-df --build-arg VITE_KC_CLIENT_ID=cm-df-app -t ghcr.io/techeazyhq-cpu/cm-df-app:0.1.0 .
```

A sandbox is a second release in its own namespace, with its own database, Redis and Pulsar tenant:

```bash
helm upgrade --install cm deploy/helm/consent-manager -n consent-manager-sandbox --create-namespace \
  -f deploy/helm/consent-manager/values-aws.yaml -f deploy/helm/consent-manager/values-sandbox.yaml
```

## Checks (run in CI)

```bash
docker run --rm -v "$PWD/deploy/terraform:/tf" -w /tf --entrypoint sh hashicorp/terraform:1.12.2 -c 'terraform fmt -check -recursive'
docker run --rm -v "$PWD/deploy/helm:/charts" -w /charts alpine/helm:3.16.4 lint consent-manager -f consent-manager/values-aws.yaml
```

When `infra/opa/policies/cns_authz.rego` changes, copy it to `deploy/helm/consent-manager/files/opa/`. CI fails until
the two match.
