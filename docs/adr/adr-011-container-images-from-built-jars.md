# ADR-011: Service images wrap the built JAR, and run unprivileged

- **Status:** Accepted
- **Date:** 2026-08-30 (Phase 1); non-root 2026-10-10 (#160)

## Context

The plan was Cloud Native Buildpacks (`bootBuildImage`), with no hand-written Dockerfiles. Spring Boot 3.3.4's
buildpack tooling calls a pinned Docker Engine API (v1.24) that current Docker Desktop rejects with a 400 on image
pulls, so `bootBuildImage` cannot run in the development environment.

## Decision

1. **One shared Dockerfile, `infra/docker/Dockerfile.service`**, wraps a JAR already built by `./gradlew bootJar`:
   `eclipse-temurin:21-jre-alpine`, the JAR copied in, nothing compiled inside the image. Every backend service uses
   it with a `JAR_PATH` build argument.
2. **The JVM's DNS cache is short** (`networkaddress.cache.ttl=10`), because Compose gives a restarted container a new
   address and the default JVM caches lookups forever.
3. **The container runs as an unprivileged user** (`cm`), with the JAR read-only and owned by root.

## Consequences

- Images build anywhere Docker runs, quickly, from the same JAR the tests ran against.
- One Dockerfile to keep patched; Dependabot watches its base image.
- Buildpacks remain the preferred route once the Spring Boot upgrade brings tooling that speaks a current Docker API;
  revisit then.
- Each image is scanned in CI ([ADR-012](adr-012-quality-gates.md)).
