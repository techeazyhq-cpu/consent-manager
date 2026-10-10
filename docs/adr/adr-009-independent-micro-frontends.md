# ADR-009: Three independent micro-frontends, no shared shell

- **Status:** Accepted
- **Date:** 2026-08-30 (Phase 1), recorded 2026-10-10

## Context

Principals, fiduciaries and administrators have nothing in common in the UI except a look and a way to sign in. They
are different audiences with different release needs and threat models: the DP portal faces the public, CM Admin
must never be served to it. A shell with Module Federation, the first design, would couple their builds and runtimes,
and sharing React state across separately built bundles is a known source of subtle bugs (two copies of one context).

## Decision

1. **Three standalone React + Vite apps**, each with its own build, port, deployment and Keycloak session:
   `mf-dp-portal` (5174), `mf-df-app` (5175), `mf-cm-admin` (5176).
2. **Shared code is shared as source, not at runtime:** `auth-kit` (Keycloak PKCE session), `api-client` (typed calls
   to the gateway) and `ui-kit` (components and styles) are pnpm workspace packages compiled into each app.
3. **Every call goes through `cm-gw`.** The apps never call a service directly; the gateway's CORS configuration lists
   exactly these origins.
4. **The DF app authenticates as the fiduciary's API client** (client credentials, [ADR-008](adr-008-identity-keycloak-and-passwordless-sign-in.md)),
   the same path a fiduciary's own systems use; the DP portal and CM Admin use the passwordless browser sign-in.

## Consequences

- An app can be released, rolled back or taken down without touching the others, and the public DP portal's bundle
  contains no administrator code.
- A change to a shared package is rebuilt into all three apps; there is no runtime version skew to manage.
- Some duplication between apps (routing, layout) is accepted.
