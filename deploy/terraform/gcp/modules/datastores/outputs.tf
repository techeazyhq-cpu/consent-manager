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

output "postgres_private_ip" {
  description = "Private address of PostgreSQL; it moves with the primary across a regional failover."
  value       = google_sql_database_instance.postgres.private_ip_address
}

output "postgres_jdbc_url" {
  description = <<-EOT
    config.database.url for the Helm chart. verify-ca, because the server certificate names the instance, not its
    address; the CA signs only this instance, so a certificate that chains to it can only be this server (ADR-042).
  EOT
  value = format(
    "jdbc:postgresql://%s:5432/consent_manager?sslmode=verify-ca&sslrootcert=/etc/db-ca/ca-bundle.pem",
    google_sql_database_instance.postgres.private_ip_address,
  )
}

output "postgres_server_ca_certificate" {
  description = "PEM of the CA that signs this instance's server certificate, for the db-ca-bundle ConfigMap."
  value       = nonsensitive(google_sql_database_instance.postgres.server_ca_cert[0].cert)
}

output "redis_host" {
  description = "config.redis.host for the Helm chart (TLS with the instance's CA, AUTH from Secret Manager)."
  value       = google_redis_instance.redis.host
}

output "redis_port" {
  description = "config.redis.port for the Helm chart."
  value       = google_redis_instance.redis.port
}

output "redis_server_ca_certificate" {
  description = "PEM of the CA that signs the Redis server certificate, for the redis-ca ConfigMap."
  value       = nonsensitive(google_redis_instance.redis.server_ca_certs[0].cert)
}

output "secret_name_prefix" {
  description = "Prefix of the Secret Manager entries, e.g. <prefix>db-password."
  value       = "${var.name}-"
}

output "secret_ids" {
  description = "IDs of the Secret Manager entries, for the External Secrets service account's IAM bindings."
  value       = { for name, secret in google_secret_manager_secret.chart : name => secret.id }
}

output "backup_bucket_name" {
  description = "Bucket in the recovery region for logical dumps."
  value       = google_storage_bucket.backups.name
}

output "postgres_dedicated" {
  description = "Per service with its own instance (ADR-074, ADR-096): the address, port, database, user, the Secret Manager entry holding the owner's password and the PEM of the CA that signs that instance (add it to the db-ca-bundle ConfigMap: each instance has its own CA), for that service's `database:` block in the Helm values."
  value = {
    for service, instance in google_sql_database_instance.dedicated : service => {
      host            = instance.private_ip_address
      port            = 5432
      name            = var.dedicated_databases[service].db_name
      user            = var.dedicated_databases[service].username
      password_secret = google_secret_manager_secret.dedicated_owner_password[service].secret_id
      server_ca       = nonsensitive(instance.server_ca_cert[0].cert)
    }
  }
}
