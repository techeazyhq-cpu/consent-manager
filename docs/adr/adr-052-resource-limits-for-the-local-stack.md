# ADR-052: CPU and memory limits for the local stack

- **Status:** Accepted
- **Date:** 2026-10-20
- **Relates to:** [ADR-037](adr-037-running-the-sandbox.md) (the sandbox on the same laptop)

## Context

The whole platform runs on one laptop for development: 8 Spring services, Keycloak, Pulsar, Postgres, Redis, OPA and
Mailpit, plus notification-service's own stack. On a 15.6 GB, 12-thread laptop, with Docker allowed 10 GB and every
thread, Windows became slow whenever the stacks were up. Measured idle:

- **CPU:** both Pulsars together used about 7.5 cores. The broker itself used about 0.15; the rest was the health
  check. `bin/pulsar-admin brokers healthcheck` starts a whole JVM every 10 seconds, and with 12 cores visible each
  start takes several of them.
- **Memory:** no container had a limit. Each JVM could size its heap to a quarter of Docker's 10 GB (about 2.4 GB), so
  long sessions kept growing.

## Decision

1. **Every container has a CPU and memory ceiling**, in `infra/docker-compose.yml` and in the notification-service
   overlay (`infra/notification-service/compose.cm-ports.yml`):

   | Containers | CPUs | Memory | Measured, idle |
   |---|---|---|---|
   | The 8 Spring services | 1.0 | 512 MB | 150–250 MB |
   | Sandbox services (`--profile sandbox`) | 0.5 | 384 MB | |
   | Keycloak | 1.5 | 1 GB | 500–630 MB |
   | Pulsar | 1.5 | 1 GB | 560 MB |
   | notification-service's Pulsar | 1.5 | 1.5 GB | 1.2 GB |
   | Postgres, `pulsar-init` | 1.0 | 512 MB | 80 MB |
   | notification-service's three APIs | 1.0 | 512 MB | |
   | Redis, Mailpit, OPA, catchers, UIs | 0.25 | 128 MB | under 25 MB |

2. **Each JVM sizes itself from its limit.** The services run with `-XX:MaxRAMPercentage=60 -XX:+UseSerialGC
   -XX:+ExitOnOutOfMemoryError`, so the heap is about 300 MB of a 512 MB limit. A service that runs out of memory
   exits and shows as such, rather than thrashing. notification-service's images already use
   `MaxRAMPercentage=75`.
3. **Pulsar is health-checked over HTTP:** `curl http://localhost:8080/admin/v2/brokers/health` asks the running
   broker the same question in-process. The consent-manager Pulsar fell from about 340% CPU to under 20%.
4. **Overridable for bigger runs.** `CM_SVC_CPUS` and `CM_SVC_MEM` (services), `CM_SBX_CPUS` and `CM_SBX_MEM`
   (sandbox), and `NS_APP_CPUS` and `NS_APP_MEM` (notification-service's APIs). Raise them for performance testing,
   whose figures (VAPT) were measured without limits.
5. **Docker's own share** is the person's machine setting, not the repository's. The recommendation is
   `%UserProfile%\.wslconfig` with `processors=8` and `memory=8GB`, so that Windows keeps 4 threads and about 7.5 GB
   whatever the containers do.

## Consequences

- Idle, the two stacks use about 0.4 of a CPU and 4.3 GB, against about 8 CPUs before. No container can take more
  than its share.
- **Slower cold starts.** Keycloak takes about two minutes with 1.5 CPUs. Services starting together queue for the
  CPU instead of taking all of it.
- A service near its limit fails loudly (exit on out-of-memory) instead of slowing the machine. Raise the limit when
  a feature needs more.
- Production is unchanged: the Helm chart has its own requests and limits (ADR-042).
