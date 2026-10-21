# Security and performance tests

Run these against the **local stack** only (`infra/docker-compose.yml`, gateway on `localhost:9000`). They sign people
in with emailed codes read from the common Mailpit (`localhost:8025`, ADR-053), register a test fiduciary, issue
API keys and raise consent requests. Results and findings: [docs/security/vapt-and-performance.md](../docs/security/vapt-and-performance.md).

| Path | What |
|---|---|
| `security/setup-keys.mjs` | Two fiduciaries' API keys (A: Batch Test Co, B: Security Test B Ltd on PREMIUM), a revoked key, and one given consent each. Writes them to `KEYS_FILE`; prints only ids |
| `security/vapt.mjs` | 68 scripted checks: authentication, signatures, replay, one fiduciary against another's data, privilege, input, headers, abuse |
| `security/zap-scan.sh` | OWASP ZAP's API scan from `docs/api/df-api.openapi.yaml`, through `signing-proxy.mjs`, on a private Docker network |
| `security/signing-proxy.mjs` | Signs each request a scanner sends (CM1, ADR-041), so the scan reaches what is behind authentication |
| `perf/df-api.js` | k6 load tests: `smoke`, `mix` (expected traffic), `auth` (API key against OAuth), `ramp` (to the knee) |
| `perf/run-benchmarks.sh` | The benchmark set in the report: the mix at 50, 75 and 100 requests a second, `auth`, `ramp` |
| `security/teardown-keys.mjs` | Revokes every key the kit issued, at both fiduciaries, including earlier runs' |

```bash
export KEYS_FILE="$HOME/cm-keys.json"      # outside the repository: it holds keys and signing secrets
node tests/security/setup-keys.mjs
node tests/security/vapt.mjs --json build/reports/vapt.json
tests/security/zap-scan.sh
```

```bash
docker run --rm -i --add-host host.docker.internal:host-gateway -v "$KEYS_FILE:/keys.json:ro" -v "$PWD/tests/perf:/perf:ro" -e SCENARIO=mix -e RATE=100 grafana/k6:1.3.0 run /perf/df-api.js
```

Each run of `setup-keys.mjs` issues new keys: run `teardown-keys.mjs` when done. `run-benchmarks.sh` signs in afresh
for the OAuth side, as console tokens last minutes.
