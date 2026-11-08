# ADR-072: A release pipeline that publishes signed images and the chart

- **Status:** Accepted
- **Date:** 2026-11-08
- **Issue:** #102

## Context

CI builds and scans the service images and lints the Helm chart on every change, but nothing published them: a deployment needed
someone to build the images by hand and push them to a registry the chart's `image.registry` points at, with no record of what
commit an image came from and no way for a cluster to check it. The chart's default tag is its `appVersion`, so the missing piece
was a version that exists as images and as a chart.

## Decision

1. **A tag is a release.** Pushing `vMAJOR.MINOR.PATCH` runs `.github/workflows/release.yml`; nothing else publishes.
2. **Scan, then push.** Each of the nine service images is built, scanned with the same Trivy gate as CI (a CRITICAL flaw with a
   fix stops the release), and only then pushed to `ghcr.io/<owner>/<service>:<version>`, labelled with the source repository,
   version and commit.
3. **Sign by digest, keyless.** Each pushed image is signed with cosign using the workflow's OIDC identity; no signing key exists to
   be stolen or rotated. A cluster can require the signature with its admission policy.
4. **The chart is an OCI artifact** (`oci://ghcr.io/<owner>/charts/consent-manager`) with the tag as its version and app version, so
   `helm upgrade --install --version <v>` pulls images and chart that were released together.
5. **A GitHub release** lists the signed digests and the verify command.
6. **No deployment from here.** Deploying needs cloud credentials and a human decision per environment; the release only produces
   what a deployment installs (runbook).

## Not yet / limits

- **Web apps are not published.** Vite bakes each environment's gateway and Keycloak addresses into them (ADR-042), so each
  environment builds its own with `Dockerfile.ui`. A runtime-configured build would let them be released once; it is a change to the apps.
- **The workflow has not run.** It is checked with actionlint and by running its image-naming logic against the built JARs, but a
  first real run needs a tag; make the first one `v0.1.0-rc`-style only after changing the tag filter, or expect to fix small
  things on `v0.1.0`.
- **No SBOM or provenance attestation yet,** and no automatic bump of `Chart.yaml` in the repository (the packaged chart takes its version from the tag).
- **No continuous deployment.** An environment is updated by running Helm with the published chart.

## Options considered

- **Publishing on every merge to `main`:** an image per commit that nobody asked for, and no meaning attached to the tag.
- **Long-lived signing keys:** something to protect and rotate; keyless ties the signature to this repository's workflow.
- **Deploying from the pipeline:** puts cloud credentials in GitHub and makes every tag a production change.
