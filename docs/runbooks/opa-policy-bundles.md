# Runbook: the authorisation policy bundle

For whoever changes the consent policy or runs the platform. Why it is built this way is in
[ADR-068](../adr/adr-068-signed-policy-bundles.md). The policy itself is `infra/opa/policies/cns_authz.rego` (ADR-009).

## Changing the policy

1. Edit `infra/opa/policies/cns_authz.rego` and its tests in `cns_authz_test.rego`; a change to a rule without a test is not finished.
2. Copy the policy to the chart's copy (`deploy/helm/consent-manager/files/opa/cns_authz.rego`): CI fails when the two differ.
3. Build it yourself the way CI will:
   ```bash
   scripts/opa-bundle.sh                       # checks, tests, builds dist/opa/cm-authz-<version>.tar.gz and its manifest
   ```
   It needs Docker (it runs the same OPA image the platform runs) or a local `opa` (`OPA_BIN=opa`).
4. Open the pull request. CI builds the bundle and proves, with real OPA servers, that a signed bundle loads and a tampered or
   wrongly signed one is refused. The bundle and its manifest are on the run as the `opa-bundle` artifact.
5. After it merges to `main`, CI builds the **signed** bundle (if the signing key is configured) and uploads it. **Publish it** (below).

## One-time: the signing key

```bash
openssl genrsa -out cm-policy-private.pem 3072
openssl rsa -in cm-policy-private.pem -pubout -out cm-policy-public.pem
```

- The **private** key goes into the repository's secret `OPA_BUNDLE_SIGNING_KEY` (the whole PEM) and nowhere else: not a file in the
  repository, not a chat, not a ticket. Keep an offline copy in the platform owner's vault.
- The key's **name** is the repository variable `OPA_BUNDLE_KEY_ID` (default `cm-policy`). It is a label the cluster uses, not part
  of the bundle.
- The **public** key goes into the chart's values, `opa.bundle.publicKey`, with the same name in `opa.bundle.keyId`.

## Publishing a bundle (by hand, until a pipeline step does it)

1. Download the `opa-bundle` artifact of the `main` run. Check `manifest-<version>.json`: `"signed": true`, the `sha256` of the
   `.tar.gz`, the `revision` is the commit you meant.
2. Copy `cm-authz-<version>.tar.gz` to the bundle host under `opa.bundle.serviceUrl`, **then** make the stable name the chart asks for
   (`opa.bundle.resource`, default `cm-authz.tar.gz`) point at it. OPA polls every `opa.bundle.pollSeconds` (60 to 120 s) and loads
   the new one if its signature verifies. Keep the old versions: that is the rollback.
3. Check it took: each OPA pod's logs (`--log-format=json`) show the bundle activating, and a consent check still decides.

## Turning it on in a cluster

```yaml
opa:
  bundle:
    enabled: true
    serviceUrl: https://policy.example.com/bundles
    keyId: cm-policy
    publicKey: |
      -----BEGIN PUBLIC KEY-----
      ...
      -----END PUBLIC KEY-----
```

The chart refuses to render with `enabled` and no `serviceUrl` or no `publicKey`. With it on, OPA is **not ready until a bundle has loaded
and verified**, so a release with a wrong URL or key waits rather than running without a policy. The gateway answers 503 for
consent checks until then (ADR-057): safe, and visible.

## Rolling back

Point `cm-authz.tar.gz` at the previous version's file. OPA loads it at its next poll. Nothing in the cluster changes.

## Rotating the signing key

The chart takes one key, so rotation means a short, planned gap or a manual two-key step:

1. Generate the new pair. Build and publish a bundle signed with the **new** key under a **new** stable name.
2. Add the new public key to OPA's configuration alongside the old one, with the new key's name, and point the bundle at the new
   name with that `keyid` (edit the ConfigMap `<release>-opa-policies`'s `config.yaml`, then restart OPA).
3. Once every OPA runs the new bundle, update `opa.bundle.publicKey` and `keyId` in the values to the new pair, and retire the old key.

(Making this one step in the chart is on the ADR's "not yet" list.)

## When something is wrong

| What you see | What it means | What to do |
|---|---|---|
| OPA pods never become ready after turning bundles on | no bundle has loaded and verified: the URL is wrong, the host is unreachable, the file is not there, or its signature is not by `publicKey` | `kubectl logs` the OPA pod: it says which. Fix the URL or publish a bundle signed with the key in the values |
| Consent checks answer 503 | OPA is not ready, or unreachable (ADR-057) | as above; this is the safe failure |
| The pipeline's bundle is `"signed": false` on `main` | `OPA_BUNDLE_SIGNING_KEY` is not set for that run (or it is a fork's PR, which never gets secrets) | set the secret; re-run |
| `Verifying the signature ...` fails in the build | the public key does not match the private key | the script derives the public key from the private one; a failure means the key file is damaged: regenerate |
| A bundle must be stopped now | someone published a bad one | repoint the stable name at the previous version (above) |

## Checks that run

- Every build: `opa check --strict`, the policy tests, and the bundle build.
- Every build on CI: the end-to-end test above (a signed bundle loads; a tampered one and a wrongly signed one are refused), the chart
  rendering in bundle mode against Kubernetes' schemas, and the chart refusing a bundle with no key.
