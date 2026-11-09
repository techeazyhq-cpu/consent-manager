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

# A service that has outgrown the shared instance, or must not share fate with it (ADR-074, ADR-096). Same protections as the shared
# one: regional high availability, private only, TLS required, point-in-time recovery, backups in the recovery region, deletion
# protection. Its password is a Secret Manager entry of its own. The alert policies of alerts.tf match every Cloud SQL instance in
# the project, so they cover these too.

ephemeral "random_password" "dedicated_owner" {
  for_each = var.dedicated_databases

  length  = 40
  special = false
}

resource "google_sql_database_instance" "dedicated" {
  for_each = var.dedicated_databases

  name                = "${var.name}-${each.key}"
  project             = var.project_id
  region              = var.region
  database_version    = "POSTGRES_16"
  deletion_protection = true

  settings {
    tier                        = coalesce(each.value.tier, var.postgres_tier)
    edition                     = "ENTERPRISE"
    availability_type           = "REGIONAL"
    disk_type                   = "PD_SSD"
    disk_size                   = coalesce(each.value.disk_size_gb, var.postgres_disk_size_gb)
    disk_autoresize             = true
    deletion_protection_enabled = true
    retain_backups_on_delete    = true
    user_labels                 = var.labels

    ip_configuration {
      ipv4_enabled    = false
      private_network = var.network_id
      ssl_mode        = "ENCRYPTED_ONLY"
      server_ca_mode  = "GOOGLE_MANAGED_INTERNAL_CA"
    }

    backup_configuration {
      enabled                        = true
      start_time                     = "01:00"
      location                       = var.disaster_recovery_region
      point_in_time_recovery_enabled = true
      transaction_log_retention_days = 7

      backup_retention_settings {
        retained_backups = 30
        retention_unit   = "COUNT"
      }
    }

    maintenance_window {
      day          = 7
      hour         = 3
      update_track = "stable"
    }

    insights_config {
      query_insights_enabled = true
    }

    dynamic "database_flags" {
      for_each = local.postgres_flags

      content {
        name  = database_flags.key
        value = database_flags.value
      }
    }
  }

  depends_on = [terraform_data.private_services_access]
}

locals {
  # the shared instance's flags (postgres.tf), for the dedicated ones
  postgres_flags = {
    log_min_duration_statement          = "1000"
    idle_in_transaction_session_timeout = "60000"
    log_checkpoints                     = "on"
    log_connections                     = "on"
    log_disconnections                  = "on"
    log_lock_waits                      = "on"
    log_temp_files                      = "0"
    "cloudsql.enable_pgaudit"           = "off"
  }
}

resource "google_sql_database" "dedicated" {
  for_each = var.dedicated_databases

  name     = each.value.db_name
  project  = var.project_id
  instance = google_sql_database_instance.dedicated[each.key].name

  lifecycle {
    prevent_destroy = true
  }
}

resource "google_sql_user" "dedicated_owner" {
  for_each = var.dedicated_databases

  name                = each.value.username
  project             = var.project_id
  instance            = google_sql_database_instance.dedicated[each.key].name
  password_wo         = ephemeral.random_password.dedicated_owner[each.key].result
  password_wo_version = 1
}

# The owner's password, for the application secret under the key the output names.
resource "google_secret_manager_secret" "dedicated_owner_password" {
  for_each = var.dedicated_databases

  secret_id = "${var.name}-${each.key}-db-password"
  project   = var.project_id
  labels    = var.labels

  replication {
    user_managed {
      replicas {
        location = var.region
      }
      replicas {
        location = var.disaster_recovery_region
      }
    }
  }

  lifecycle {
    prevent_destroy = true
  }
}

resource "google_secret_manager_secret_version" "dedicated_owner_password" {
  for_each = var.dedicated_databases

  secret                 = google_secret_manager_secret.dedicated_owner_password[each.key].id
  secret_data_wo         = ephemeral.random_password.dedicated_owner[each.key].result
  secret_data_wo_version = 1
}
