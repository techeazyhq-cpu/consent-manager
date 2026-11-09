{{/* Copyright 2026 Vasantha Kumar. Licensed under the Apache License, Version 2.0. */}}

{{- define "cm.labels" -}}
app.kubernetes.io/name: {{ .name }}
app.kubernetes.io/instance: {{ .root.Release.Name }}
app.kubernetes.io/part-of: consent-manager
app.kubernetes.io/version: {{ .root.Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .root.Release.Service }}
helm.sh/chart: {{ printf "%s-%s" .root.Chart.Name .root.Chart.Version }}
{{- end -}}

{{- define "cm.selectorLabels" -}}
app.kubernetes.io/name: {{ .name }}
app.kubernetes.io/instance: {{ .root.Release.Name }}
{{- end -}}

{{- define "cm.image" -}}
{{- $tag := .root.Values.image.tag | default .root.Chart.AppVersion -}}
{{- printf "%s/%s:%s" .root.Values.image.registry .name $tag -}}
{{- end -}}

{{- define "cm.secretRef" -}}
valueFrom: {secretKeyRef: {name: {{ .root.Values.secrets.existingSecret }}, key: {{ .key }}{{ if .optional }}, optional: true{{ end }}}}
{{- end -}}

{{/* Non-root, no privilege escalation, no capabilities, read-only root filesystem (writable /tmp only). */}}
{{- define "cm.podSecurityContext" -}}
runAsNonRoot: true
runAsUser: {{ .uid }}
runAsGroup: {{ .uid }}
fsGroup: {{ .uid }}
seccompProfile: {type: RuntimeDefault}
{{- end -}}

{{- define "cm.containerSecurityContext" -}}
allowPrivilegeEscalation: false
readOnlyRootFilesystem: true
capabilities: {drop: [ALL]}
{{- end -}}

{{/* Where the database server's CA bundle is mounted when config.database.tls.caConfigMap is set (ADR-085). */}}
{{- define "cm.dbCaPath" -}}/etc/db-ca/ca-bundle.pem{{- end -}}

{{/* The JDBC connection parameters: config.database.tls when it has a mode, else jdbcParams as written. */}}
{{- define "cm.jdbcParams" -}}
{{- $tls := .tls | default dict -}}
{{- if $tls.mode -}}
sslmode={{ $tls.mode }}{{ if $tls.caConfigMap }}&sslrootcert={{ include "cm.dbCaPath" . }}{{ end }}
{{- else -}}{{ .jdbcParams }}{{- end -}}
{{- end -}}

{{/* The same for R2DBC, whose mode names are upper case and with underscores. */}}
{{- define "cm.r2dbcParams" -}}
{{- $tls := .tls | default dict -}}
{{- if $tls.mode -}}
sslMode={{ $tls.mode | upper | replace "-" "_" }}{{ if $tls.caConfigMap }}&sslRootCert={{ include "cm.dbCaPath" . }}{{ end }}
{{- else -}}{{ .r2dbcParams }}{{- end -}}
{{- end -}}

{{/* Environment for one service: its "docker" profile, then the datastores, identity and secrets it uses. */}}
{{- define "cm.serviceEnv" -}}
{{- $root := .root -}}{{- $name := .name -}}{{- $s := .settings -}}
{{- $c := $root.Values.config -}}{{- $db := mergeOverwrite (deepCopy $c.database) ($s.database | default dict) -}}{{- $dbKey := $db.passwordKey | default "DB_PASSWORD" -}}{{- $migUser := $db.migrationUser | default $db.user -}}{{- $migKey := $db.migrationPasswordKey | default $dbKey -}}
{{- /* least privilege (ADR-090): the query login is the schema's own, unless this service names one itself */ -}}
{{- $lp := false -}}{{- if and (($c.database.leastPrivilege | default dict).enabled) $s.schema (not (hasKey ($s.database | default dict) "user")) -}}{{- $lp = true -}}{{- end -}}
{{- $runUser := ternary (printf "cm_%s_app" ($s.schema | default "")) $db.user $lp -}}{{- $runKey := ternary (printf "RUNTIME_DB_PASSWORD_%s" ($s.schema | default "" | upper)) $dbKey $lp -}}
- {name: SPRING_PROFILES_ACTIVE, value: docker}
- {name: JAVA_TOOL_OPTIONS, value: "-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"}
{{- if $root.Values.tracing.enabled }}
- {name: CM_TRACING_SAMPLING, value: {{ $root.Values.tracing.sampling | quote }}}
- {name: CM_TRACING_ENDPOINT, value: {{ required "tracing.endpoint is required when tracing.enabled" $root.Values.tracing.endpoint | quote }}}
{{- end }}
{{- if $root.Values.metrics.enabled }}
- {name: CM_METRICS_EXPOSURE, value: {{ ternary "health,info,gateway,prometheus" "health,info,prometheus" (eq $name "cm-gw") | quote }}}
{{- if eq $name "cm-gw" }}
- {name: CM_GW_MANAGEMENT_PORT, value: {{ $root.Values.metrics.gatewayPort | quote }}}
{{- end }}
{{- end }}
{{- with $s.schema }}
- name: SPRING_R2DBC_URL
  value: {{ printf "r2dbc:postgresql://%s:%v/%s?schema=%s&%s" $db.host $db.port $db.name . (include "cm.r2dbcParams" $db) | quote }}
- {name: SPRING_R2DBC_USERNAME, value: {{ $runUser | quote }}}
- name: SPRING_R2DBC_PASSWORD
  {{- include "cm.secretRef" (dict "root" $root "key" $runKey) | nindent 2 }}
- name: SPRING_FLYWAY_URL
  value: {{ printf "jdbc:postgresql://%s:%v/%s?%s" $db.host $db.port $db.name (include "cm.jdbcParams" $db) | quote }}
- {name: SPRING_FLYWAY_USER, value: {{ $migUser | quote }}}
- name: SPRING_FLYWAY_PASSWORD
  {{- include "cm.secretRef" (dict "root" $root "key" $migKey) | nindent 2 }}
{{- end }}
- {name: CM_PULSAR_SERVICE_URL, value: {{ $c.pulsar.url | quote }}}
{{- if ($c.pulsar.tls | default dict).caConfigMap }}
- {name: CM_PULSAR_TLS_TRUST_CERTS_FILE, value: "/etc/pulsar-ca/ca-bundle.pem"}
{{- end }}
{{- if $c.pulsar.tokenFromSecret }}
- name: CM_PULSAR_AUTH_TOKEN
  {{- include "cm.secretRef" (dict "root" $root "key" "PULSAR_AUTH_TOKEN") | nindent 2 }}
{{- end }}
- {name: CM_SECURITY_ISSUER_BASE, value: {{ printf "%s/realms/" $c.keycloak.publicUrl | quote }}}
- {name: CM_SECURITY_JWKS_BASE, value: {{ printf "%s/realms/" $c.keycloak.internalUrl | quote }}}
{{- if $s.redis }}
- {name: SPRING_DATA_REDIS_HOST, value: {{ $c.redis.host | quote }}}
- {name: SPRING_DATA_REDIS_PORT, value: {{ $c.redis.port | quote }}}
- {name: SPRING_DATA_REDIS_DATABASE, value: {{ $c.redis.database | default 0 | quote }}}
- {name: SPRING_DATA_REDIS_SSL_ENABLED, value: {{ $c.redis.ssl | quote }}}
{{- if ($c.redis.tls | default dict).caConfigMap }}
- {name: SPRING_DATA_REDIS_SSL_BUNDLE, value: redis}
- {name: SPRING_SSL_BUNDLE_PEM_REDIS_TRUSTSTORE_CERTIFICATE, value: "file:/etc/redis-ca/ca-bundle.pem"}
{{- end }}
{{- if $c.redis.passwordFromSecret }}
- name: SPRING_DATA_REDIS_PASSWORD
  {{- include "cm.secretRef" (dict "root" $root "key" "REDIS_PASSWORD") | nindent 2 }}
{{- end }}
{{- end }}
{{- if $s.keycloakAdmin }}
- {name: CM_KEYCLOAK_BASEURL, value: {{ $c.keycloak.internalUrl | quote }}}
- {name: CM_KEYCLOAK_ADMINUSERNAME, value: {{ $c.keycloak.adminUser | quote }}}
- name: CM_KEYCLOAK_ADMINPASSWORD
  {{- include "cm.secretRef" (dict "root" $root "key" "KEYCLOAK_ADMIN_PASSWORD") | nindent 2 }}
{{- end }}
{{- if eq $name "cm-df-reg-svc" }}
- name: CM_DFREG_SECRETS_KEY
  {{- include "cm.secretRef" (dict "root" $root "key" "DFREG_SECRETS_KEY") | nindent 2 }}
- {name: CM_KEYCLOAK_TOKENHOST, value: ""}
{{- end }}
{{- if eq $name "cm-bil-svc" }}
# what invoices are issued in (ADR-079)
- {name: CM_BIL_CURRENCY, value: {{ $c.billing.currency | quote }}}
- {name: CM_BIL_TAX_LABEL, value: {{ $c.billing.taxLabel | quote }}}
- {name: CM_BIL_TAX_BPS, value: {{ $c.billing.taxBps | quote }}}
{{- end }}
{{- if eq $name "cm-cns-cmd-svc" }}
# the address consent tokens name for their status list (ADR-077)
- {name: CM_CNS_TKN_STATUS_LIST_BASE_URL, value: {{ printf "https://%s" $root.Values.ingress.apiHost | quote }}}
{{- end }}
{{- if eq $name "cm-ntf-svc" }}
- {name: CM_NTF_PROVIDER, value: {{ $c.notificationService.provider | quote }}}
- {name: NTF_SVC_BASE_URL, value: {{ $c.notificationService.baseUrl | quote }}}
# who may ask this service to send a sign-in code (ADR-083): the same value is Keycloak's NTF_API_KEY server setting
- name: CM_NTF_INTERNAL_API_KEY
  {{- include "cm.secretRef" (dict "root" $root "key" "NTF_INTERNAL_API_KEY" "optional" (not ($root.Values.secrets.requireInternalKey | default false))) | nindent 2 }}
# the key before it, accepted too while it is being changed (ADR-101); leave the secret key out when no change is under way
- name: CM_NTF_INTERNAL_API_KEY_PREVIOUS
  {{- include "cm.secretRef" (dict "root" $root "key" "NTF_INTERNAL_API_KEY_PREVIOUS" "optional" true) | nindent 2 }}
- {name: SPRING_MAIL_HOST, value: {{ $c.mail.host | quote }}}
- {name: SPRING_MAIL_PORT, value: {{ $c.mail.port | quote }}}
# the key that hashes the addresses of principals who chose how they hear of requests (ADR-066): required, never a default
- name: CM_NTF_EMAIL_FINGERPRINT_KEY
  {{- include "cm.secretRef" (dict "root" $root "key" "NTF_EMAIL_FINGERPRINT_KEY") | nindent 2 }}
- name: NTF_SVC_API_KEY
  {{- include "cm.secretRef" (dict "root" $root "key" "NTF_SVC_API_KEY" "optional" true) | nindent 2 }}
- name: NTF_SVC_SIGNING_SECRET
  {{- include "cm.secretRef" (dict "root" $root "key" "NTF_SVC_SIGNING_SECRET" "optional" true) | nindent 2 }}
{{- end }}
{{- if $s.consentTokenKeys }}
- {name: CM_CNS_TKN_KEYS_PATH, value: /var/lib/cm/keys/jwks.json}
- {name: CM_CNS_TKN_CREATE_KEYS, value: "false"}
- {name: CM_CNS_RCPT_KEYS_PATH, value: /var/lib/cm/keys/receipt-jwks.json}
- {name: CM_CNS_RCPT_CREATE_KEYS, value: "false"}
- name: CM_CNSCMD_SECRETS_KEY
  {{- include "cm.secretRef" (dict "root" $root "key" "CNSCMD_SECRETS_KEY") | nindent 2 }}
- {name: CM_WEBHOOKS_ALLOW_PRIVATE, value: "false"}
{{- end }}
{{- if eq $name "cm-gw" }}
- {name: CM_PDP_BASEURL, value: {{ printf "http://%s-opa:8181" $root.Release.Name | quote }}}
{{- end }}
{{- if $root.Values.sandbox.enabled }}
- {name: CM_SANDBOX_ENABLED, value: "true"}
- {name: CM_SECURITY_REALMPREFIX, value: {{ $root.Values.sandbox.realmPrefix | quote }}}
- {name: CM_PULSAR_TENANT, value: {{ $root.Values.sandbox.pulsarTenant | quote }}}
- {name: CM_SANDBOX_PRODUCTION_ISSUERBASE, value: {{ $root.Values.sandbox.productionIssuerBase | quote }}}
{{- if eq $name "cm-df-reg-svc" }}
- {name: CM_KEYCLOAK_REALM, value: {{ printf "%scm-df" $root.Values.sandbox.realmPrefix | quote }}}
{{- end }}
{{- end }}
- {name: LOG_FORMAT, value: {{ $c.logFormat | quote }}}
{{- range $k, $v := $c.extraEnv }}
- {name: {{ $k }}, value: {{ $v | quote }}}
{{- end }}
{{- end -}}
