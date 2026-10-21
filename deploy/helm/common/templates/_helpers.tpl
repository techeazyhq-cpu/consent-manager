{{/* Copyright 2026 Vasantha Kumar. Licensed under the Apache License, Version 2.0. */}}

{{- define "common.labels" -}}
app.kubernetes.io/name: {{ .name }}
app.kubernetes.io/instance: {{ .root.Release.Name }}
app.kubernetes.io/part-of: common
app.kubernetes.io/version: {{ .root.Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .root.Release.Service }}
helm.sh/chart: {{ printf "%s-%s" .root.Chart.Name .root.Chart.Version }}
{{- end -}}

{{- define "common.selectorLabels" -}}
app.kubernetes.io/name: {{ .name }}
app.kubernetes.io/instance: {{ .root.Release.Name }}
{{- end -}}

{{- define "common.secretRef" -}}
valueFrom: {secretKeyRef: {name: {{ .root.Values.secrets.existingSecret }}, key: {{ .key }}}}
{{- end -}}

{{- define "common.podSecurityContext" -}}
runAsNonRoot: true
runAsUser: {{ .uid }}
runAsGroup: {{ .uid }}
fsGroup: {{ .uid }}
seccompProfile: {type: RuntimeDefault}
{{- end -}}

{{/* readOnly: false for servers that write next to their binaries (Pulsar's config, Keycloak's build). */}}
{{- define "common.containerSecurityContext" -}}
allowPrivilegeEscalation: false
readOnlyRootFilesystem: {{ .readOnly }}
capabilities: {drop: [ALL]}
{{- end -}}

{{/* A ReadWriteOnce volume claim template for a server's StatefulSet. */}}
{{- define "common.volumeClaim" -}}
- metadata:
    name: data
  spec:
    accessModes: [ReadWriteOnce]
    {{- with .storageClassName }}
    storageClassName: {{ . }}
    {{- end }}
    resources: {requests: {storage: {{ .storage }}}}
{{- end -}}
