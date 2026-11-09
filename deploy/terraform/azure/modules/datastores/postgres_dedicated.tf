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

# A service that has outgrown the shared server, or must not share fate with it (ADR-074, ADR-095). Same protections as the shared
# one: zone-redundant high availability, private only, TLS 1.2 or later, geo-redundant backups, a delete lock. The server goes in the
# same delegated subnet and private DNS zone, and its password is a vault secret of its own.

ephemeral "random_password" "dedicated_owner" {
  for_each = var.dedicated_databases

  length  = 40
  special = false
}

resource "azurerm_postgresql_flexible_server" "dedicated" {
  for_each = var.dedicated_databases

  name                = "${var.name}-${each.key}"
  resource_group_name = var.resource_group_name
  location            = var.location
  version             = "16"
  sku_name            = coalesce(each.value.sku_name, var.postgres_sku_name)

  administrator_login               = each.value.username
  administrator_password_wo         = ephemeral.random_password.dedicated_owner[each.key].result
  administrator_password_wo_version = 1

  authentication {
    password_auth_enabled         = true
    active_directory_auth_enabled = false
  }

  delegated_subnet_id           = var.postgres_subnet_id
  private_dns_zone_id           = azurerm_private_dns_zone.postgres.id
  public_network_access_enabled = false

  zone = var.postgres_primary_zone
  high_availability {
    mode                      = "ZoneRedundant"
    standby_availability_zone = var.postgres_standby_zone
  }

  storage_mb        = coalesce(each.value.storage_mb, var.postgres_storage_mb)
  auto_grow_enabled = true

  backup_retention_days        = var.backup_retention_days
  geo_redundant_backup_enabled = true

  maintenance_window {
    day_of_week  = 0
    start_hour   = 3
    start_minute = 0
  }

  tags = var.tags

  depends_on = [azurerm_private_dns_zone_virtual_network_link.postgres]

  lifecycle {
    prevent_destroy = true
  }
}

locals {
  dedicated_server_settings = {
    require_secure_transport            = "ON"
    ssl_min_protocol_version            = "TLSv1.2"
    log_min_duration_statement          = "1000"
    idle_in_transaction_session_timeout = "60000"
    log_checkpoints                     = "on"
    log_connections                     = "on"
  }
}

resource "azurerm_postgresql_flexible_server_configuration" "dedicated" {
  for_each = {
    for pair in setproduct(keys(var.dedicated_databases), keys(local.dedicated_server_settings)) :
    "${pair[0]}/${pair[1]}" => { service = pair[0], name = pair[1] }
  }

  name      = each.value.name
  server_id = azurerm_postgresql_flexible_server.dedicated[each.value.service].id
  value     = local.dedicated_server_settings[each.value.name]
}

resource "azurerm_postgresql_flexible_server_database" "dedicated" {
  for_each = var.dedicated_databases

  name      = each.value.db_name
  server_id = azurerm_postgresql_flexible_server.dedicated[each.key].id
  charset   = "UTF8"
  collation = "en_US.utf8"

  lifecycle {
    prevent_destroy = true
  }
}

resource "azurerm_management_lock" "dedicated" {
  for_each = var.dedicated_databases

  name       = "${var.name}-${each.key}-cannot-delete"
  scope      = azurerm_postgresql_flexible_server.dedicated[each.key].id
  lock_level = "CanNotDelete"
  notes      = "A service's system of record. Remove this lock deliberately before deleting the server."
}

# The owner's password, for the application secret under the key the output names.
resource "azurerm_key_vault_secret" "dedicated_owner_password" {
  for_each = var.dedicated_databases

  name             = "${each.key}-db-password"
  key_vault_id     = azurerm_key_vault.secrets.id
  value_wo         = ephemeral.random_password.dedicated_owner[each.key].result
  value_wo_version = 1
  content_type     = "PostgreSQL owner password of the server of ${each.key} (ADR-095)"

  depends_on = [azurerm_role_assignment.terraform_writes_secrets]

  lifecycle {
    prevent_destroy = true
  }
}
