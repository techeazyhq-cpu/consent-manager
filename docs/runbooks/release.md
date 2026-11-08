# Runbook: releasing and installing a version

[ADR-072](../adr/adr-072-release-pipeline.md).

## Cut a release

CI must be green on `main`. Then:

```bash
git tag v0.2.0 && git push origin v0.2.0
```

The `Release` workflow builds, scans, pushes and signs the nine images, pushes the chart, and creates a GitHub release listing the
digests. If the scan fails the release stops before anything is published; fix it (or accept it in `.trivyignore` with a reason
and an expiry) and push a new tag, never move one.

## Verify an image

```bash
cosign verify ghcr.io/techeazyhq-cpu/cm-gw:0.2.0 \
  --certificate-identity-regexp 'https://github.com/techeazyhq-cpu/consent-manager/' \
  --certificate-oidc-issuer https://token.actions.githubusercontent.com
```

## Verify what an image contains and where it came from

Each image has a CycloneDX bill of materials and a SLSA provenance statement attached as signed attestations, and the bills of
materials are on the GitHub release:

```bash
ID='https://github.com/techeazyhq-cpu/consent-manager/'
ISSUER=https://token.actions.githubusercontent.com
cosign verify-attestation --type cyclonedx ghcr.io/techeazyhq-cpu/cm-gw:0.2.0   --certificate-identity-regexp "$ID" --certificate-oidc-issuer "$ISSUER" | jq -r '.payload | @base64d | fromjson | .predicate.components | length'
cosign verify-attestation --type slsaprovenance1 ghcr.io/techeazyhq-cpu/cm-gw:0.2.0   --certificate-identity-regexp "$ID" --certificate-oidc-issuer "$ISSUER" | jq -r '.payload | @base64d | fromjson | .predicate.buildDefinition.resolvedDependencies[0]'
```

When a vulnerability is announced, search the bills of materials of the versions in use for the affected library.

## Install it

```bash
helm upgrade --install cm oci://ghcr.io/techeazyhq-cpu/charts/consent-manager --version 0.2.0 \
  -n consent-manager -f values-<cloud>.yaml
```

The chart's `appVersion` is the version, so the images match without setting `image.tag`. The web apps are built for each
environment (`infra/docker/Dockerfile.ui`) and are not part of the release.

## Roll back

`helm rollback cm <revision> -n consent-manager`. Database migrations only move forward; a release that migrated needs a fixed
release rather than an older one (see the migration notes in the release).
