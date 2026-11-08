# ADR-086: A bill of materials and provenance for every released image

- **Status:** Accepted
- **Date:** 2026-11-08
- **Issue:** #102

## Context

ADR-072 publishes each service image scanned and signed, so a cluster can tell the image is ours. It cannot tell what is in it, which
matters on the day a library is announced as vulnerable ("are we running it, and where?"), nor which commit and workflow built it.
Both were the two missing items in ADR-072's own list.

## Decision

1. **A CycloneDX bill of materials per image,** made from the image as built, before it is pushed (Trivy, the same tool that
   scans it): every library and operating system package in it with versions. It is signed and attached to the image's digest
   as an attestation (`cosign attest --type cyclonedx`), and the files are attached to the GitHub release.
2. **A SLSA provenance statement per image,** attached the same way (`--type slsaprovenance1`): the workflow, the tag, the commit and
   the run that built it. Both attestations are signed with the keyless identity of the release workflow, so verifying them (the
   certificate's identity is this repository's workflow, issued by GitHub's token service) says they came from this release
   process and not from a copy of the image.
3. **Everything happens in the release job after the scan passes,** so a release that fails the scan has no attestation and no image.
4. **Checked in pieces:** the bill of materials was generated from a real built image (245 components, CycloneDX 1.6); the provenance
   is built by the command in the workflow from sample values and is valid; both were attached to an image in a local registry with
   `cosign attest` and verified with `cosign verify-attestation` (with a test key, since keyless needs the workflow's identity);
   and `actionlint` accepts the workflow. The workflow as a whole has still not run on a tag.

## Not yet / limits

- **The provenance is the workflow's own statement** about what it did, signed by its identity. It is not the isolated,
  hardened-builder level of SLSA that a dedicated generator provides; it tells a verifier which workflow and commit, and that the
  signature could only have been made by that workflow.
- **The chart has no bill of materials,** and the web apps are not released (ADR-072).
- **Nothing consumes the bills yet.** A scheduled job that scans the published bills for newly announced vulnerabilities, or an
  admission policy that requires the attestations, are the uses; the runbook shows how to read one by hand.
- **The attestations are recorded in the public transparency log** (Rekor), as keyless signing requires; that log is permanent and
  public, and it holds the image digest, the signing certificate and a hash, not the bill of materials itself.

## Options considered

- **GitHub's own build-provenance action:** the better provenance, tied to GitHub's attestation store; it signs one subject per
  call and puts the statement outside the registry. The registry-attached form here travels with the image wherever it is copied.
- **Syft or the Gradle CycloneDX plugin:** Trivy is already in the job for the scan and reads the image, so the bill is of what
  is actually in it, base image included, not only of what the build declared.
