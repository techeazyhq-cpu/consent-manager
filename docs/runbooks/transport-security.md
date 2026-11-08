# Runbook: transport security

[ADR-073](../adr/adr-073-transport-hardening.md).

## The edge

Set in the ingress-nginx ConfigMap (the chart cannot; they belong to the controller):

```yaml
ssl-protocols: "TLSv1.2 TLSv1.3"
ssl-prefer-server-ciphers: "false"
use-http2: "true"        # the default; HTTP/2 is negotiated by ALPN on the TLS handshake
```

Check from outside:

```bash
curl -sI https://api.example.com/api/status | grep -i -E "strict-transport|content-security|permissions-policy"
curl -sI --http2 https://api.example.com/api/status | head -1          # HTTP/2 ...
openssl s_client -connect api.example.com:443 -tls1_1 </dev/null 2>&1 | grep -i "alert\|handshake failure"   # must fail
```

`ingress.hardening.limitRps` is per client address. A fiduciary behind one NAT that sends bursts will see 503 from the edge:
raise `limitRps` for the release, or give it its own ingress, before disabling the limit.

## Mutual TLS between pods

1. Install the mesh (Istio or Linkerd) and mesh the ingress controller's namespace too.
2. `helm upgrade ... --set mesh.provider=istio` (or `linkerd`).
3. Check: from a pod outside the mesh, `curl http://cm-gw.consent-manager:9000/actuator/health` must be refused;
   from the ingress, `https://api.example.com/api/status` must still answer.
4. Monitoring: put Prometheus in the mesh, or allow its namespace in `networkPolicy.metricsNamespaceLabels` and, under Istio STRICT,
   scrape through the sidecar (Istio merges the `prometheus.io/*` annotations) or exempt the metrics port.

Rolling it out: Istio's `PERMISSIVE` mode (set `PeerAuthentication` by hand) first, confirm every client is in the mesh, then STRICT.
