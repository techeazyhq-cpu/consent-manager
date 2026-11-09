# Copyright 2026 Vasantha Kumar
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# @author Vasantha Kumar <vasantha.kumar@hotmail.com>

output "cluster_name" {
  description = "AKS cluster: az aks get-credentials --name <this> --resource-group <resource_group_name>."
  value       = azurerm_kubernetes_cluster.cluster.name
}

output "resource_group_name" {
  description = "Resource group of the environment."
  value       = azurerm_resource_group.environment.name
}

output "helm_values" {
  description = "Environment-specific values for deploy/helm/consent-manager, merged over values-azure.yaml."
  value = {
    config = {
      database = { url = module.datastores.postgres_jdbc_url }
      redis    = { host = module.datastores.redis_host }
    }
    # the logical backup CronJob (ADR-097): where it uploads and under which identity. Set logicalBackup.enabled to turn it on.
    logicalBackup = {
      provider    = "azure"
      destination = "https://${module.datastores.backup_storage_account_name}.blob.core.windows.net/${module.datastores.logical_dumps_container_name}"
      serviceAccount = {
        name        = "cm-logical-backup"
        annotations = { "azure.workload.identity/client-id" = azurerm_user_assigned_identity.workloads["logical-backup"].client_id }
      }
      podLabels = { "azure.workload.identity/use" = "true" }
    }
    # a service with a server of its own (ADR-074, ADR-095): its `database:` block. The password is the vault secret named in
    # postgres_dedicated, synced into the application secret under passwordKey.
    services = {
      for service, db in module.datastores.postgres_dedicated : service => {
        database = {
          host        = db.host
          port        = db.port
          name        = db.name
          user        = db.user
          passwordKey = "${upper(replace(service, "-", "_"))}_DB_PASSWORD"
        }
      }
    }
    networkPolicy = {
      datastoreEgress = [
        {
          to    = [{ ipBlock = { cidr = local.subnets.postgres } }]
          ports = [{ protocol = "TCP", port = 5432 }]
        },
        {
          to    = [{ ipBlock = { cidr = local.subnets.private_endpoints } }]
          ports = [{ protocol = "TCP", port = 10000 }]
        },
        # Pulsar and Keycloak live in the common namespace, which the chart's egress policy opens (ADR-054).
      ]
    }
  }
}

output "manifest_values" {
  description = "Values for the placeholders of deploy/k8s/azure/*.yaml."
  value = {
    KEY_VAULT_URI                = module.datastores.key_vault_uri
    EXTERNAL_SECRETS_CLIENT_ID   = azurerm_user_assigned_identity.workloads["external-secrets"].client_id
    LOGICAL_BACKUP_CLIENT_ID     = azurerm_user_assigned_identity.workloads["logical-backup"].client_id
    POSTGRES_FQDN                = module.datastores.postgres_fqdn
    BACKUP_STORAGE_ACCOUNT       = module.datastores.backup_storage_account_name
    POSTGRES_SUBNET_CIDR         = local.subnets.postgres
    PRIVATE_ENDPOINT_SUBNET_CIDR = local.subnets.private_endpoints
  }
}
