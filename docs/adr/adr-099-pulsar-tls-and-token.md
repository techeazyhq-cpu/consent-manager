# ADR-099: Pulsar over TLS, with a token when the broker asks for one

- **Status:** Accepted
- **Date:** 2026-11-09
- **Issue:** #99
- **Builds on:** [ADR-085](adr-085-database-tls-verification.md), [ADR-098](adr-098-redis-trusts-its-own-ca.md)

## Context

Every event between the services (the outbox, the erasure saga, the projections) goes through Pulsar. The services' Pulsar clients were
built eight times, each as `PulsarClient.builder().serviceUrl(url).build()`: a plain connection, no way to verify a broker or present a
token. A managed broker, or one outside the cluster's trusted network, has both.

## Decision

1. **One factory,** `PulsarClients.create(url)` in `cm-evt-kit`, used by all eight services. With nothing set it is the plain client it
   always was.
2. **`CM_PULSAR_TLS_TRUST_CERTS_FILE`** makes the client verify a `pulsar+ssl://` broker against that CA bundle and check the broker's
   host name. **`CM_PULSAR_AUTH_TOKEN`** is sent with Pulsar's token authentication. Blank values count as not set, and neither value is
   logged.
3. **Helm:** `config.pulsar.tls.caConfigMap` (and `caKey`) mounts the bundle at `/etc/pulsar-ca/` in every pod and sets the first
   variable; `config.pulsar.tokenFromSecret: true` takes the second from the secret key `PULSAR_AUTH_TOKEN`. Both off by default, so
   nothing changes for the in-cluster broker on its plain port.
4. **The configuration is unit-tested** (nothing set, a bundle, a token, blanks, and that the real client builder accepts it).

## Checked

- **Unit tests** for the configuration (`PulsarClientsTest`) and every module compiles with the factory in place.
- **The chart renders** with the bundle and the token for AWS: all nine pods get the variables, the mount and the volume; nothing
  renders without them; kubeconform passes.
- **Live, against a Pulsar standalone with a TLS port (6651)** (a certificate for the name `pulsar-tls` from a CA made for the test): the
  notification service's image, given `pulsar+ssl://pulsar-tls:6651` and the CA, completed the handshake and talked to the broker (the
  broker answered its requests, with "Namespace not found" because the test broker has no `cm` tenant, which is an application reply
  and so proves the TLS session). The same image without the CA logged `Error during handshake` and could not talk to it.

## Not yet / limits

- **The broker's own side is the operator's:** this makes the clients able to speak TLS and a token; turning the broker's TLS port and
  authentication on (and the common chart's Pulsar) is not part of this change.
- **The token was checked in unit tests only,** not against a broker that requires one.
- **Pulsar's admin API** (HTTP) is not used by the services and is not covered.
- **No client certificate (mutual TLS)** and no other authentication than a token (OAuth2, for a managed service) is supported.
- **A rotated broker CA or token** needs the ConfigMap or secret updated and the pods restarted.
- **The gateway also gets the mount and the variables** (the chart applies them to every service), though it does not use Pulsar.
- **Terraform does not name a Pulsar CA or token:** there is no managed Pulsar in it; the values are the operator's.
