# ADR-098: The services trust the cache server's own CA

- **Status:** Accepted
- **Date:** 2026-11-09
- **Issue:** #99
- **Builds on:** [ADR-085](adr-085-database-tls-verification.md) (the same for the database)

## Context

The cache (Redis) holds the rate-limit counters, the status page and the read-model cache. Every cloud's Redis is reached over TLS
(`config.redis.ssl: true` in each `values-<cloud>.yaml`). AWS's ElastiCache certificate chains to a public CA the JVM trusts. GCP's
Memorystore signs with a CA of its own, which no JVM trusts, and Terraform's comment says "the chart trusts exactly that CA" and its
output names a `redis-ca` ConfigMap, but the chart had nothing that used one: a service on Memorystore could not connect over TLS.

## Decision

1. **`config.redis.tls.caConfigMap`** (and `caKey`, default `ca-bundle.pem`). When set, every service that uses Redis mounts that
   ConfigMap at `/etc/redis-ca/` and is told to trust exactly that bundle: a Spring SSL bundle named `redis` with that PEM as its
   trust store, used by the Redis client (`SPRING_DATA_REDIS_SSL_BUNDLE`, `SPRING_SSL_BUNDLE_PEM_REDIS_TRUSTSTORE_CERTIFICATE`).
2. **Off unless set,** so nothing changes for a deployment on a CA the JVM already trusts, or without TLS.
3. **Only the Redis connection** uses it; the JVM's other connections keep the default trust store, and the database has its own
   bundle (ADR-085).

## Checked

- The chart renders with it for GCP: the three services that use Redis (the gateway, the query service and billing) each get the two
  environment variables, the mount and the volume; nothing renders without it.
- **Live, against a TLS-only Redis (password, no plain port) with a CA made for the test:** the gateway image was started with the
  two variables and the CA mounted read-only; its status service wrote its `status:*` keys into that Redis (ten or more present).
  The same image without the variables could not connect (`Unable to connect to Redis`) and wrote none. So the setting is what makes
  the connection work, and a Redis whose certificate does not chain to the bundle is refused.

## Not yet / limits

- **The ConfigMap is made by hand** from `terraform output -raw redis_server_ca_certificate` (as the database's is); GCP's
  `helm_values` names it (`redis-ca`). Azure's and AWS's Redis certificates are expected to chain to public CAs the JVM trusts (not checked here), so they need nothing.
- **Not run on Memorystore:** the test used a Redis of my own with a CA of my own.
- **A rotated Redis CA** needs the ConfigMap updated and the pods restarted; Memorystore announces the rotation.
- **Pulsar's TLS is still open** (the other half of the issue's "Redis and Pulsar TLS"), and so is Keycloak's database connection.
- **Hostname verification** is the client's default for the connection; it was seen to hold in the test only in that the right name
  connected (the negative case above was the missing CA, not a wrong name).
