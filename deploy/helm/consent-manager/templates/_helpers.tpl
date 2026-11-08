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

{{/* Environment for one service: its "docker" profile, then the datastores, identity and secrets it uses. */}}
{{- define "cm.serviceEnv" -}}
{{- $root := .root -}}{{- $name := .name -}}{{- $s := .settings -}}
{{- $c := $root.Values.config -}}{{- $db := mergeOverwrite (deepCopy $c.database) ($s.database | default dict) -}}{{- $dbKey := $db.passwordKey | default "DB_PASSWORD" -}}{{- $migUser := $db.migrationUser | default $db.user -}}{{- $migKey := $db.migrationPasswordKey | default $dbKey -}}
- {name: SPRING_PROFILES_ACTIVE, value: docker}
- {name: JAVA_TOOL_OPTIONS, value: "-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"}
{{- if $root.Values.metrics.enabled }}
- {name: CM_METRICS_EXPOSURE, value: {{ ternary "health,info,gateway,prometheus" "health,info,prometheus" (eq $name "cm-gw") | quote }}}
{{- if eq $name "cm-gw" }}
- {name: CM_GW_MANAGEMENT_PORT, value: {{ $root.Values.metrics.gatewayPort | quote }}}
{{- end }}
{{- end }}
{{- with $s.schema }}
- name: SPRING_R2DBC_URL
  value: {{ printf "r2dbc:postgresql://%s:%v/%s?schema=%s&%s" $db.host $db.port $db.name . $db.r2dbcParams | quote }}
- {name: SPRING_R2DBC_USERNAME, value: {{ $db.user | quote }}}
- name: SPRING_R2DBC_PASSWORD
  {{- include "cm.secretRef" (dict "root" $root "key" $dbKey) | nindent 2 }}
- name: SPRING_FLYWAY_URL
  value: {{ printf "jdbc:postgresql://%s:%v/%s?%s" $db.host $db.port $db.name $db.jdbcParams | quote }}
- {name: SPRING_FLYWAY_USER, value: {{ $migUser | quote }}}
- name: SPRING_FLYWAY_PASSWORD
  {{- include "cm.secretRef" (dict "root" $root "key" $migKey) | nindent 2 }}
{{- end }}
- {name: CM_PULSAR_SERVICE_URL, value: {{ $c.pulsar.url | quote }}}
- {name: CM_SECURITY_ISSUER_BASE, value: {{ printf "%s/realms/" $c.keycloak.publicUrl | quote }}}
- {name: CM_SECURITY_JWKS_BASE, value: {{ printf "%s/realms/" $c.keycloak.internalUrl | quote }}}
{{- if $s.redis }}
- {name: SPRING_DATA_REDIS_HOST, value: {{ $c.redis.host | quote }}}
- {name: SPRING_DATA_REDIS_PORT, value: {{ $c.redis.port | quote }}}
- {name: SPRING_DATA_REDIS_DATABASE, value: {{ $c.redis.database | default 0 | quote }}}
- {name: SPRING_DATA_REDIS_SSL_ENABLED, value: {{ $c.redis.ssl | quote }}}
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
  {{- include "cm.secretRef" (dict "root" $root "key" "NTF_INTERNAL_API_KEY" "optional" true) | nindent 2 }}
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
