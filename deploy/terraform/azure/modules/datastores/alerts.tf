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
#
# The managed-service counterpart of postgres-recovery.rules.yml: storage running out, the server down, and Azure's
# own health events (failovers, outages, degraded service) for the database and the cache.

resource "azurerm_monitor_action_group" "operations" {
  name                = "${var.name}-operations"
  resource_group_name = var.resource_group_name
  short_name          = "notifyops"

  dynamic "email_receiver" {
    for_each = toset(var.alert_email_addresses)

    content {
      name                    = replace(email_receiver.value, "/[^a-zA-Z0-9]/", "-")
      email_address           = email_receiver.value
      use_common_alert_schema = true
    }
  }

  tags = var.tags
}

locals {
  postgres_alert_kinds = {
    storage_nearly_full = { metric = "storage_percent", aggregation = "Average", operator = "GreaterThan", threshold = 85 }
    server_down         = { metric = "is_db_alive", aggregation = "Minimum", operator = "LessThan", threshold = 1 }
  }

  # every server: the shared one and each dedicated one (ADR-095)
  postgres_servers = merge(
    { "postgres" = azurerm_postgresql_flexible_server.postgres.id },
    { for service, server in azurerm_postgresql_flexible_server.dedicated : service => server.id },
  )
  postgres_alerts = {
    for pair in setproduct(keys(local.postgres_servers), keys(local.postgres_alert_kinds)) :
    "${pair[0]}/${pair[1]}" => merge(local.postgres_alert_kinds[pair[1]], { server = pair[0], kind = pair[1] })
  }
}

# the alerts of the shared server were keyed by their kind alone; they now carry the server too, without being recreated
moved {
  from = azurerm_monitor_metric_alert.postgres["storage_nearly_full"]
  to   = azurerm_monitor_metric_alert.postgres["postgres/storage_nearly_full"]
}

moved {
  from = azurerm_monitor_metric_alert.postgres["server_down"]
  to   = azurerm_monitor_metric_alert.postgres["postgres/server_down"]
}

resource "azurerm_monitor_metric_alert" "postgres" {
  for_each = local.postgres_alerts

  name                = "${var.name}-${each.value.server == "postgres" ? "postgres" : "postgres-${each.value.server}"}-${replace(each.value.kind, "_", "-")}"
  resource_group_name = var.resource_group_name
  scopes              = [local.postgres_servers[each.value.server]]
  severity            = each.value.kind == "server_down" ? 0 : 2
  frequency           = "PT1M"
  window_size         = "PT5M"

  criteria {
    metric_namespace = "Microsoft.DBforPostgreSQL/flexibleServers"
    metric_name      = each.value.metric
    aggregation      = each.value.aggregation
    operator         = each.value.operator
    threshold        = each.value.threshold
  }

  action {
    action_group_id = azurerm_monitor_action_group.operations.id
  }

  tags = var.tags
}

resource "azurerm_monitor_activity_log_alert" "resource_health" {
  name                = "${var.name}-datastore-health"
  resource_group_name = var.resource_group_name
  location            = "global"
  scopes              = concat(values(local.postgres_servers), [azurerm_managed_redis.redis.id])
  description         = "Azure reports the database or the cache unavailable or degraded."

  criteria {
    category = "ResourceHealth"

    resource_health {
      current  = ["Unavailable", "Degraded"]
      previous = ["Available"]
    }
  }

  action {
    action_group_id = azurerm_monitor_action_group.operations.id
  }

  tags = var.tags
}
