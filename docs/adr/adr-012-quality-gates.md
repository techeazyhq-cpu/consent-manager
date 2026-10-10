# ADR-012: Quality gates on every pull request, as notification-service has them

- **Status:** Accepted
- **Date:** 2026-10-10 (#152, #153, #161)

## Context

Until October 2026 nothing checked a change before it reached `main`: no CI, no coverage, no dependency or secret
scanning, no branch protection. The first scans found six HIGH vulnerabilities in the frontend lockfile, a root
container, and CRITICAL vulnerabilities in every service image from two-year-old dependencies. notification-service
already runs a proven set of gates; consent-manager adopts the same standard.

## Decision

1. **Project governance:** Apache 2.0 with a header on every source file (checked by `scripts/license-headers.mjs`),
   `CONTRIBUTING.md` (branches and pull requests only, Conventional Commits, tests first, an ADR for significant
   decisions), a pull request template, and Dependabot for Gradle, pnpm, Docker and GitHub Actions with major upgrades
   left to deliberate pull requests.
2. **Required checks on every pull request** (`.github/workflows`):
   - *Backend build and tests*: license headers, `./gradlew build` (unit tests, PostgreSQL integration tests with
     Testcontainers, every service's ArchUnit layering test), and the coverage ratchet;
   - *Frontend build and audit*: a locked install, the three apps build, no HIGH or CRITICAL runtime advisory;
   - *Service images build*: every image builds and is scanned by Trivy;
   - *Dependency and secret scan*: Trivy on the repository for vulnerabilities, secrets and misconfiguration;
   - *CodeQL* for Java and TypeScript.
3. **Coverage is a ratchet.** One JaCoCo report across all modules; `coverageVerification` fails below
   `coverageMinimum` in `build.gradle.kts`, which starts at today's 11 % of lines and is only ever raised.
4. **Branch protection** from `.github/branch-protection.json` ([runbook](../branch-protection.md)): pull requests
   only, the checks above required, administrators included, no force pushes.
5. **Actions are pinned to commit SHAs**, the same ones notification-service uses.

## Consequences

- A red check blocks the merge, for the owner too.
- The image scan starts as report-only: blocking on today's CRITICAL findings would also block the dependency
  upgrades that fix them. The Spring Boot and dependency upgrade turns it into a gate on CRITICAL findings, as in
  notification-service.
- 11 % is low; the Testcontainers tasks (#121, #126, #129, #133) are how it rises.
- Sonar is not part of the gate yet; CodeQL and Trivy cover security, and coverage is measured by JaCoCo.
